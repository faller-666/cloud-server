package com.company.cloud.auth.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录限流 + 连续失败锁定（对标任务书 R-A01）
 *  - 每 IP 每分钟限流 5 次
 *  - 连续失败 5 次锁定 15 分钟，返回剩余时间
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginThrottle {

    public static final int LOCKED_EXPIRE_MINUTES = 15;

    private final StringRedisTemplate redis;

    private static final String RATE_KEY = "auth:rate:";
    private static final String FAIL_KEY = "auth:fail:";
    private static final String LOCK_KEY = "auth:lock:";

    /**
     * 是否触发限流（每 IP 每分钟 > 5 次）
     */
    public boolean isRateLimited(String ip) {
        String k = RATE_KEY + ip;
        Long n = redis.opsForValue().increment(k);
        if (n != null && n == 1) {
            redis.expire(k, Duration.ofMinutes(1));
        }
        return n != null && n > 5;
    }

    /**
     * 记录一次登录失败；返回已连续失败次数
     */
    public long recordFailure(String ip) {
        String k = FAIL_KEY + ip;
        Long n = redis.opsForValue().increment(k);
        if (n != null && n >= 5) {
            lock(ip);
            redis.delete(k);
        }
        return n == null ? 0 : n;
    }

    /**
     * 是否已锁定；若锁定返回剩余分钟数（0 表示未锁定）
     */
    public long lockedRemainingMinutes(String ip) {
        Long ttl = redis.getExpire(LOCK_KEY + ip);
        return (ttl == null || ttl < 0) ? 0 : Math.max(1, ttl / 60 + 1);
    }

    private void lock(String ip) {
        redis.opsForValue().set(LOCK_KEY + ip, "1", Duration.ofMinutes(LOCKED_EXPIRE_MINUTES));
        log.warn("账号/IP 已锁定: {} ({} 分钟)", ip, LOCKED_EXPIRE_MINUTES);
    }

    /**
     * 登录成功后清空失败计数（也常按用户名维度清空，这里以 IP 为例）
     */
    public void clearFailure(String ip) {
        redis.delete(FAIL_KEY + ip);
    }
}