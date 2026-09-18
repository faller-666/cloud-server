package com.company.cloud.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 双 token 服务（对标任务书 R-A02）
 *  - access token：2 小时
 *  - refresh token：14 天，refresh 接口轮换
 *  - 签发时在 claims 写入 uv（用户 token 版本），供禁用账号后旧 token 失效校验（R-A03）。
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final RevocationService revocationService;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.access-ttl-minutes}") long accessMinutes,
            @Value("${app.jwt.refresh-ttl-days}") long refreshDays,
            RevocationService revocationService) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTtl = Duration.ofMinutes(accessMinutes);
        this.refreshTtl = Duration.ofDays(refreshDays);
        this.revocationService = revocationService;
    }

    /**
     * 签发 access token，携带 userId / username / role / uv(用户token版本)，并含 jti（吊销用）
     */
    public String createAccessToken(Long userId, String username, String role) {
        return createToken(userId, username, role, accessTtl, "access");
    }

    /**
     * 签发 refresh token，轮换时生成新的 jti
     */
    public String createRefreshToken(Long userId, String username, String role) {
        return createToken(userId, username, role, refreshTtl, "refresh");
    }

    private String createToken(Long userId, String username, String role,
                               Duration ttl, String type) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttl.toMillis());
        long uv = revocationService.getUserTokenVersion(userId);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())       // jti
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("typ", type)
                .claim("uv", uv)                        // 用户 token 版本（禁用后 +1 → 旧 token 失效）
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 token，非法或过期抛异常
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getJti(Claims claims) {
        return claims.getId();
    }

    public boolean isAccessToken(Claims claims) {
        return "access".equals(claims.get("typ", String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return "refresh".equals(claims.get("typ", String.class));
    }
}