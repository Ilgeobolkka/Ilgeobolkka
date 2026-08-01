package com.example.ilgeobolkka.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
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

    @Test
    void 결제_비활성화_환경의_잉크_화면은_기본_CSP를_사용한다() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("portone.payment.enabled", "false");

        assertContentSecurityPolicy(
                environment,
                "/ink",
                SecurityConfig.BASE_CONTENT_SECURITY_POLICY);
        assertContentSecurityPolicy(
                environment,
                "/books/17",
                SecurityConfig.BASE_CONTENT_SECURITY_POLICY);
    }

    @Test
    void 결제_활성화_환경은_잉크와_도서_상세에만_결제_CSP를_사용한다() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("portone.payment.enabled", "true");

        assertContentSecurityPolicy(
                environment,
                "/ink",
                SecurityConfig.PAYMENT_CONTENT_SECURITY_POLICY);
        assertContentSecurityPolicy(
                environment,
                "/books/17",
                SecurityConfig.PAYMENT_CONTENT_SECURITY_POLICY);
        assertContentSecurityPolicy(
                environment,
                "/books",
                SecurityConfig.BASE_CONTENT_SECURITY_POLICY);
        assertContentSecurityPolicy(
                environment,
                "/ownership-payments",
                SecurityConfig.BASE_CONTENT_SECURITY_POLICY);
    }

    @Test
    void 운영_프로필은_결제_활성화_설정이_있어도_기본_CSP를_사용한다() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("portone.payment.enabled", "true");
        environment.setActiveProfiles("prod");

        assertContentSecurityPolicy(
                environment,
                "/ink",
                SecurityConfig.BASE_CONTENT_SECURITY_POLICY);
        assertContentSecurityPolicy(
                environment,
                "/books/17",
                SecurityConfig.BASE_CONTENT_SECURITY_POLICY);
    }

    private void assertContentSecurityPolicy(
            MockEnvironment environment,
            String path,
            String expectedPolicy) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();

        SecurityConfig.contentSecurityPolicyHeaderWriter(environment)
                .writeHeaders(request, response);

        assertEquals(
                expectedPolicy,
                response.getHeader("Content-Security-Policy"));
        assertEquals(
                1,
                response.getHeaders("Content-Security-Policy").size());
    }
}
