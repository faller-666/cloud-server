package com.company.cloud.auth.service;

import com.company.cloud.common.result.ErrorCode;
import com.company.cloud.common.result.BizException;
import com.company.cloud.auth.audit.AuditService;
import com.company.cloud.auth.dto.*;
import com.company.cloud.auth.entity.User;
import com.company.cloud.auth.repository.UserRepository;
import com.company.cloud.auth.security.CurrentUser;
import com.company.cloud.auth.security.JwtService;
import com.company.cloud.auth.security.LoginThrottle;
import com.company.cloud.auth.security.RevocationService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证核心服务（对标任务书 R-A01 ~ R-A04）
 *  - 登录（限流 + 锁定 + 签发双 token）
 *  - 刷新（refresh token 轮换）
 *  - 登出 / 修改密码（吊销旧 token）
 *  - 首登强制改密拦截
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginThrottle throttle;
    private final RevocationService revocationService;
    private final AuditService auditService;

    /**
     * 登录：用户名 + 密码；限流；连续失败锁定；签发双 token
     */
    public LoginResponse login(LoginRequest req, HttpServletRequest http) {
        String ip = clientIp(http);

        // 1. 限流：每 IP 每分钟 5 次
        if (throttle.isRateLimited(ip)) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }

        // 2. 锁定检查
        long remain = throttle.lockedRemainingMinutes(ip);
        if (remain > 0) {
            throw new BizException(ErrorCode.ACCOUNT_LOCKED,
                    ErrorCode.ACCOUNT_LOCKED.getMessage() +
                            "（" + remain + " 分钟后可再试）");
        }

        // 3. 用户校验
        User user = userRepository.findByUsername(req.getUsername()).orElse(null);
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throttle.recordFailure(ip);
            auditService.loginFail(req.getUsername(), ip);
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }

        // 4. 禁用检查
        if (user.isDisabled()) {
            auditService.loginFail(user.getUsername(), ip);
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 5. 解锁计数
        throttle.clearFailure(ip);

        // 6. 签发双 token；回填最近登录时间（V1002）
        user.setLastLoginAt(java.time.OffsetDateTime.now());
        userRepository.save(user);

        String access = jwtService.createAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refresh = jwtService.createRefreshToken(user.getId(), user.getUsername(), user.getRole());

        auditService.loginSuccess(user.getId(), user.getUsername(), ip);
        return new LoginResponse(access, refresh, userMap(user));
    }

    /**
     * 刷新 access：校验 refresh token（未吊销）→ 轮换签发新的双 token
     */
    @Transactional
    public LoginResponse refresh(RefreshRequest req) {
        Claims claims;
        try {
            claims = jwtService.parse(req.getRefreshToken());
        } catch (Exception e) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        // 吊销的 refresh 不可用
        if (revocationService.isRevoked(jwtService.getJti(claims))) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        Long userId = Long.valueOf(claims.getSubject());
        User user = userRepository.findById(userId).orElseThrow(() -> new BizException(ErrorCode.TOKEN_INVALID));
        if (user.isDisabled()) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        // 用户级 token 版本校验：账号被禁用/重置密码后版本 +1，旧 refresh token 不可再用（R-A03）
        long tokenUv = claims.get("uv", Long.class) == null ? 0L : claims.get("uv", Long.class);
        if (revocationService.getUserTokenVersion(userId) != tokenUv) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
        // 轮换：吊销旧 refresh，签发新双 token
        revocationService.revokeWithRemaining(jwtService.getJti(claims), refreshRemainingMillis(claims));
        String access = jwtService.createAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refresh = jwtService.createRefreshToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginResponse(access, refresh, userMap(user));
    }

    /**
     * 登出：吊销当前 access token（刷新 token 由客户端一并丢弃；如需严格吊销也在此处理）
     */
    @Transactional
    public void logout(CurrentUser cu, HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                revocationService.revokeAccess(jwtService.getJti(claims));
            } catch (Exception ignored) {
                // token 已过期则无需吊销
            }
        }
    }

    /**
     * 修改密码：校验旧密码 → 更新（置 must_change_password=false）→ 吊销该用户全部旧 token
     * 注：吊销全部旧 token 依赖于用户维度记录，此处以吊销当前 access 为例；
     *     严格实现可引入 per-user token 索引（如 user:{id}:tokens 集合）以支持全量吊销。
     */
    @Transactional
    public void changePassword(CurrentUser cu, ChangePasswordRequest req) {
        User user = userRepository.findById(cu.getId())
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        auditService.changePassword(user.getId(), user.getUsername());
        // 用户自助改密 → 用户 token 版本 +1，该账号此前签发的全部 token 立即失效（R-A03）
        long ver = revocationService.bumpUserTokenVersion(user.getId());
        log.info("改密成功 userId={}，已吊销全部历史 token (uv={})", user.getId(), ver);
    }

    /**
     * 当前用户资料（对标任务书 R-A09）
     */
    public Map<String, Object> profile(CurrentUser cu) {
        User user = userRepository.findById(cu.getId())
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        return userMap(user);
    }

    private Map<String, Object> userMap(User user) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", user.getId());
        m.put("username", user.getUsername());
        m.put("role", user.getRole());
        m.put("quotaBytes", user.getQuotaBytes());
        m.put("usedBytes", user.getUsedBytes());
        m.put("mustChangePassword", user.getMustChangePassword());
        m.put("nickname", user.getNickname());
        m.put("email", user.getEmail());
        m.put("lastLoginAt", user.getLastLoginAt());
        return m;
    }

    private String clientIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private long refreshRemainingMillis(Claims claims) {
        Date exp = claims.getExpiration();
        return exp == null ? 0 : Math.max(0, exp.getTime() - System.currentTimeMillis());
    }
}