package com.example.ilgeobolkka.global.config;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityConfigTest {

    private final PasswordEncoder passwordEncoder = new SecurityConfig().passwordEncoder();

    @Test
    void 신규_비밀번호는_위임_인코더의_BCrypt_형식으로_저장한다() {
        String rawPassword = "Password1!";

        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertTrue(encodedPassword.startsWith("{bcrypt}"));
        assertNotEquals(rawPassword, encodedPassword);
        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));
    }
}
