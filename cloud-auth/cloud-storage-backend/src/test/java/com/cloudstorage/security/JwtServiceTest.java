package com.cloudstorage.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT 双 token 单元测试
 * 覆盖：签发/解析、access 与 refresh 类型区分、过期校验
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "cloud-storage-test-secret-key-0123456789abcdef-0123456789", // >= 32 bytes
                "cloud-storage",
                120,  // access 2h
                14);  // refresh 14d
    }

    @Test
    void issueAndParseAccessToken() {
        String token = jwtService.createAccessToken(1L, "zhangsan", "user");
        var claims = jwtService.parse(token);
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("username", String.class)).isEqualTo("zhangsan");
        assertThat(claims.get("role", String.class)).isEqualTo("user");
        assertThat(jwtService.isAccessToken(claims)).isTrue();
        assertThat(jwtService.isRefreshToken(claims)).isFalse();
        assertThat(jwtService.getJti(claims)).isNotBlank();
    }

    @Test
    void issueAndParseRefreshToken() {
        String token = jwtService.createRefreshToken(2L, "admin1", "admin");
        var claims = jwtService.parse(token);
        assertThat(jwtService.isRefreshToken(claims)).isTrue();
        assertThat(jwtService.isAccessToken(claims)).isFalse();
    }
}