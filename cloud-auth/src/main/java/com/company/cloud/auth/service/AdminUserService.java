package com.company.cloud.auth.service;

import com.company.cloud.common.result.ErrorCode;
import com.company.cloud.common.result.BizException;
import com.company.cloud.auth.audit.AuditService;
import com.company.cloud.auth.dto.CreateUserRequest;
import com.company.cloud.auth.dto.UpdateUserRequest;
import com.company.cloud.auth.entity.User;
import com.company.cloud.auth.repository.UserRepository;
import com.company.cloud.auth.security.RevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 管理端用户管理服务（对标任务书 R-A06 ~ R-A08）
 *  - 创建用户（初始密码可随机，初始配额默认 20GB）
 *  - 禁用/启用、配额调整、角色变更
 *  - 重置密码（重置后强制首登改密）
 *  - 用户列表（分页 + 搜索 + 状态筛选）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final RevocationService revocationService;

    @Value("${app.role.default-quota-bytes}")
    private long defaultQuotaBytes;

    private final SecureRandom random = new SecureRandom();

    /**
     * 用户列表：分页、按用户名搜索、按状态筛选
     */
    public Page<User> list(String keyword, String status, Pageable pageable) {
        return userRepository.search(
                (keyword == null || keyword.isBlank()) ? null : keyword.trim(),
                (status == null || status.isBlank()) ? null : status.trim(),
                pageable);
    }

    /**
     * 创建用户；返回初始密码（随机生成时由管理员告知用户）
     */
    @Transactional
    public String create(CreateUserRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "用户名已存在");
        }
        String rawPwd = req.getInitialPassword() == null || req.getInitialPassword().isBlank()
                ? randomPassword()
                : req.getInitialPassword();
        String role = (req.getRole() == null || req.getRole().isBlank()) ? "user" : req.getRole();
        long quota = (req.getQuotaBytes() == null || req.getQuotaBytes() <= 0)
                ? defaultQuotaBytes : req.getQuotaBytes();

        User user = User.builder()
                .username(req.getUsername())
                .passwordHash(passwordEncoder.encode(rawPwd))
                .role(role)
                .quotaBytes(quota)
                .usedBytes(0L)
                .status("active")
                .mustChangePassword(true)   // 新用户首登强制改密
                .nickname(req.getNickname())
                .email(validateEmailUnique(req.getEmail(), null))
                .build();
        userRepository.save(user);
        log.info("[admin] 创建用户 username={} role={} quota={}", user.getUsername(), role, quota);
        return rawPwd; // 随机密码会在此返回，供管理员安全转交
    }

    /**
     * 更新用户：禁用/启用、调整配额、变更角色
     */
    @Transactional
    public void update(Long id, UpdateUserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));

        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            if ("disabled".equals(req.getStatus()) && !user.isDisabled()) {
                auditService.disableUser(0L, id);
                // 禁用账号 → 用户 token 版本 +1，该账号所有已签发 token 立即过期（R-A03 禁用拦截）
                long ver = revocationService.bumpUserTokenVersion(id);
                log.info("[admin] 禁用账号 userId={} 已吊销全部 token (uv={})", id, ver);
            }
            user.setStatus(req.getStatus());
        }
        if (req.getRole() != null && !req.getRole().isBlank()) {
            user.setRole(req.getRole());
        }
        if (req.getQuotaBytes() != null && req.getQuotaBytes() > 0) {
            if (req.getQuotaBytes() < user.getUsedBytes()) {
                // 目标值低于已用量 → 拒绝并提示（对标 R-A08）
                throw new BizException(ErrorCode.QUOTA_TOO_LOW,
                        ErrorCode.QUOTA_TOO_LOW.getMessage() +
                                "（当前已用量 " + user.getUsedBytes() + " 字节）");
            }
            long oldQuota = user.getQuotaBytes();
            user.setQuotaBytes(req.getQuotaBytes());
            auditService.quotaChange(0L, id, oldQuota, req.getQuotaBytes());
        }
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getEmail() != null) {
            user.setEmail(validateEmailUnique(req.getEmail(), id));
        }
        userRepository.save(user);
    }

    /**
     * 校验邮箱是否已被其他用户占用；通过则原样返回，空串归一为 null。
     */
    private String validateEmailUnique(String email, Long excludeId) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String norm = email.trim();
        userRepository.findByEmail(norm).ifPresent(existing -> {
            if (excludeId == null || !existing.getId().equals(excludeId)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "邮箱已被使用：" + norm);
            }
        });
        return norm;
    }

    /**
     * 重置密码：更新为指定/随机密码，并置 must_change_password=true（对标 R-A07）
     */
    @Transactional
    public String resetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.USER_NOT_FOUND));
        String raw = (newPassword == null || newPassword.isBlank()) ? randomPassword() : newPassword;
        user.setPasswordHash(passwordEncoder.encode(raw));
        user.setMustChangePassword(true);
        userRepository.save(user);
        // 重置密码 → 用户 token 版本 +1，该账号所有已签发 token（access/refresh）立即过期
        long ver = revocationService.bumpUserTokenVersion(id);
        log.info("[admin] 重置密码 userId={} 已吊销全部 token (uv={})", id, ver);
        return raw;
    }

    private String randomPassword() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 16);
    }
}