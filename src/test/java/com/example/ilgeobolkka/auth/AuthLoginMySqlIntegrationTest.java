package com.example.ilgeobolkka.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.reader.entity.Reader;
import com.example.ilgeobolkka.reader.service.ReaderService;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(AuthLoginMySqlIntegrationTest.CurrentReaderController.class)
@Transactional
class AuthLoginMySqlIntegrationTest {

    private static final String EMAIL = "reader@example.com";
    private static final String RAW_PASSWORD = "Valid-password1!";

    private final MockMvc mockMvc;
    private final ReaderService readerService;
    private final ServerProperties serverProperties;

    @Autowired
    AuthLoginMySqlIntegrationTest(
            MockMvc mockMvc,
            ReaderService readerService,
            ServerProperties serverProperties) {
        this.mockMvc = mockMvc;
        this.readerService = readerService;
        this.serverProperties = serverProperties;
    }

    @Test
    void 로그인은_세션_ID를_교체하고_AuthenticatedReader를_Principal로_저장한다() throws Exception {
        Reader reader = readerService.createReader(EMAIL, RAW_PASSWORD);
        MockHttpSession loginSession = new MockHttpSession();
        String sessionIdBeforeLogin = loginSession.getId();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .session(loginSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("  READER@EXAMPLE.COM  ", RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readerId").value(reader.getId()))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andReturn();

        MockHttpSession authenticatedSession =
                (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(authenticatedSession);
        assertNotEquals(sessionIdBeforeLogin, authenticatedSession.getId());

        SecurityContext securityContext = (SecurityContext) authenticatedSession.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertNotNull(securityContext);
        Authentication authentication = securityContext.getAuthentication();
        assertTrue(authentication.isAuthenticated());
        assertEquals(new AuthenticatedReader(reader.getId()), authentication.getPrincipal());

        mockMvc.perform(get("/api/test/current-reader").session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(content().string(Long.toString(reader.getId())));
    }

    @Test
    void 존재하지_않는_이메일과_잘못된_비밀번호는_같은_인증_오류를_반환한다() throws Exception {
        readerService.createReader(EMAIL, RAW_PASSWORD);

        assertInvalidCredentials("missing@example.com", RAW_PASSWORD);
        assertInvalidCredentials(EMAIL, "Wrong-password2?");
    }

    @Test
    void 로그인_세션의_비활성_만료는_2시간이다() {
        assertEquals(Duration.ofHours(2), serverProperties.getServlet().getSession().getTimeout());
    }

    private void assertInvalidCredentials(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    private static String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    @RestController
    static class CurrentReaderController {

        @GetMapping("/api/test/current-reader")
        long currentReader(@AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
            return authenticatedReader.readerId();
        }
    }
}
