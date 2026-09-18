package com.company.cloud.auth.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 吊销列表（对标任务书 R-A03）
 * key: jwt:revoked:{jti}，TTL 与 token 剩余有效期一致。
 * 登出、改密、禁用后写入吊销列表，旧 token 立即失效（不等自然过期）。
 *
 * 用户级 token 版本（disabled 拦截）：
 *  - 单独按 jti 吊销无法覆盖"该账号已签发的全部 token"（无法枚举）。
 *  - 故引入用户级版本：签发 token 时在 claims 写入 uv（= 当前用户 token 版本），
 *    禁用账号时 bumpUserTokenVersion 使版本 +1；JwtAuthFilter 校验时比对
 *    token 内 uv 与 Redis 当前版本，不一致即判定失效（40103）。
 *  - 这样禁用会使该用户所有已签发 token 立即过期，重新启用后旧 token 也不复活。
 * key: jwt:uver:{userId}（不存在视为版本 0）。
 */
@Service
@RequiredArgsConstructor
public class RevocationService {

    private static final String KEY_PREFIX = "jwt:revoked:";
    private static final String UV_KEY_PREFIX = "jwt:uver:";

    private final StringRedisTemplate redis;

    @Value("${app.jwt.access-ttl-minutes}")
    private long accessTtlMinutes;

    /**
     * 吊销一个 token（按 jti 写入，TTL=对应的剩余有效期）
     */
    public void revoke(String jti, Duration ttl) {
        redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
    }

    /**
     * 吊销 access token（TTL 用 access 剩余时间兜底）
     */
    public void revokeAccess(String jti) {
        revoke(jti, Duration.ofMinutes(accessTtlMinutes));
    }

    /**
     * 校验某 jti 是否已被吊销
     */
    public boolean isRevoked(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }

    // ---- 用户级 token 版本（禁用账号 → 该账号全部 token 过期） ----

    /**
     * 读取用户当前 token 版本；无记录视为 0。
     */
    public long getUserTokenVersion(Long userId) {
        String v = redis.opsForValue().get(UV_KEY_PREFIX + userId);
        return v == null ? 0L : Long.parseLong(v);
    }

    /**
     * 用户 token 版本 +1（禁用账号 / 重置密码等使旧 token 全部失效时调用）。
     */
    public long bumpUserTokenVersion(Long userId) {
        Long v = redis.opsForValue().increment(UV_KEY_PREFIX + userId);
        return v == null ? 0L : v;
    }

    // ---- 辅助：按剩余有效期精确吊销 ----
    public void revokeWithRemaining(String jti, long remainingMillis) {
        revoke(jti, Duration.ofMillis(Math.max(1, remainingMillis)));
    }
}