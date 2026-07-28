package com.example.ilgeobolkka.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class AuthSignupMySqlIntegrationTest {

    private static final String RAW_PASSWORD = "Valid-password1!";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    AuthSignupMySqlIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Test
    void 회원가입은_정규화한_Reader와_0잉크_계좌만_생성하고_로그인하지_않는다() throws Exception {
        MockHttpSession session = new MockHttpSession();

        String response = mockMvc.perform(post("/api/auth/signup")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("  NEW-Reader@Example.COM  ", RAW_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.readerId").isNumber())
                .andExpect(jsonPath("$.email").value("new-reader@example.com"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long readerId = Long.parseLong(response.replaceAll(".*\"readerId\":(\\d+).*", "$1"));

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM reader WHERE id = ?",
                String.class,
                readerId);
        Integer balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM ink_account WHERE reader_id = ?",
                Integer.class,
                readerId);
        Integer ledgerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ink_ledger WHERE reader_id = ?",
                Integer.class,
                readerId);

        assertTrue(passwordHash.startsWith("{bcrypt}"));
        assertTrue(passwordEncoder.matches(RAW_PASSWORD, passwordHash));
        assertEquals(0, balance);
        assertEquals(0, ledgerCount);
        assertNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));

        mockMvc.perform(get("/api/ink/balance").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void 정규화한_이메일이_같으면_중복_오류로_응답한다() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(" Duplicate@Example.com ", RAW_PASSWORD)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("duplicate@example.com", RAW_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reader WHERE email = 'duplicate@example.com'",
                        Integer.class));
    }

    @ParameterizedTest
    @MethodSource("invalidPasswords")
    void 정책에_맞지_않는_비밀번호는_계정을_생성하지_않는다(String invalidPassword) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("invalid-password@example.com", invalidPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reader WHERE email = 'invalid-password@example.com'",
                        Integer.class));
    }

    @Test
    void UTF8_64바이트_비밀번호는_허용한다() throws Exception {
        String maximumBytePassword = "Aa1!" + "가".repeat(20);

        assertEquals(64, maximumBytePassword.getBytes(StandardCharsets.UTF_8).length);

        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("maximum-password@example.com", maximumBytePassword)))
                .andExpect(status().isCreated());
    }

    @Test
    void 정규화_후_255자인_이메일은_허용한다() throws Exception {
        String maximumLengthEmail = maximumLengthEmail();

        assertEquals(255, maximumLengthEmail.length());

        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(maximumLengthEmail, RAW_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(maximumLengthEmail));
    }

    @ParameterizedTest
    @MethodSource("invalidEmails")
    void 정책에_맞지_않는_이메일은_계정을_생성하지_않는다(String invalidEmail) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(invalidEmail, RAW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reader WHERE email LIKE '%invalid-email%'",
                        Integer.class));
    }

    @ParameterizedTest
    @MethodSource("missingRequiredFields")
    void 이메일이나_비밀번호가_누락되면_입력_오류로_응답한다(String requestBody) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private static Stream<String> invalidPasswords() {
        return Stream.of(
                "Aa1!aaa",
                "Password1",
                "Password!",
                "12345678!",
                "Aa1!" + "가".repeat(21));
    }

    private static Stream<String> invalidEmails() {
        return Stream.of(
                "   ",
                "invalid-email",
                maximumLengthEmail() + "a");
    }

    private static Stream<String> missingRequiredFields() {
        return Stream.of(
                """
                {"password":"Valid-password1!"}
                """,
                """
                {"email":"missing-password@example.com"}
                """);
    }

    private static String maximumLengthEmail() {
        return "a".repeat(64)
                + "@"
                + "b".repeat(63)
                + "."
                + "c".repeat(63)
                + "."
                + "d".repeat(62);
    }

    private static String signupJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }
}
