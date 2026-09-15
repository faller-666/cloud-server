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
 */
@Service
@RequiredArgsConstructor
public class RevocationService {

    private static final String KEY_PREFIX = "jwt:revoked:";

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

    // ---- 辅助：按剩余有效期精确吊销 ----
    public void revokeWithRemaining(String jti, long remainingMillis) {
        revoke(jti, Duration.ofMillis(Math.max(1, remainingMillis)));
    }
}