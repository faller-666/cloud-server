package com.company.cloud.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guard/认证核心单元测试（登录相关密码编码验证）
 * 覆盖：bcrypt 编码与匹配、明文不落库
 */
class AuthCoreTest {

    private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder(12);
    }

    @Test
    void bcryptMatchesCorrectPassword() {
        String hash = encoder.encode("secret123");
        assertThat(encoder.matches("secret123", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }

    @Test
    void bcryptSaltRandomized() {
        String hash1 = encoder.encode("same-pwd");
        String hash2 = encoder.encode("same-pwd");
        assertThat(hash1).isNotEqualTo(hash2); // 加盐随机
    }

    @Test
    void encodedPasswordIsNotPlaintext() {
        String hash = encoder.encode("plaintext-pwd");
        assertThat(hash).doesNotContain("plaintext-pwd");
    }
}