package com.example.ilgeobolkka.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.reader.entity.Reader;
import com.example.ilgeobolkka.reader.service.ReaderService;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import({
    AuthSessionTimeoutMySqlIntegrationTest.CsrfTokenController.class,
    AuthSessionTimeoutMySqlIntegrationTest.CurrentReaderController.class
})
class AuthSessionTimeoutMySqlIntegrationTest {

    private static final String RAW_PASSWORD = "Valid-password1!";

    @LocalServerPort
    private int port;

    private final ReaderService readerService;
    private final JdbcTemplate jdbcTemplate;
    private Long createdReaderId;

    @Autowired
    AuthSessionTimeoutMySqlIntegrationTest(
            ReaderService readerService,
            JdbcTemplate jdbcTemplate) {
        this.readerService = readerService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @AfterEach
    void deleteCreatedReader() {
        if (createdReaderId != null) {
            jdbcTemplate.update("DELETE FROM reader WHERE id = ?", createdReaderId);
        }
    }

    @Test
    void 로그인_세션은_비활성_제한을_넘으면_보호_API에서_거부된다() throws Exception {
        String email = "session-timeout-" + UUID.randomUUID() + "@example.com";
        Reader reader = readerService.createReader(email, RAW_PASSWORD);
        createdReaderId = reader.getId();
        HttpClient client = sessionClient();
        IssuedCsrfToken csrfToken = issueCsrfToken(client);

        HttpResponse<String> loginResponse = client.send(
                HttpRequest.newBuilder(uri("/api/auth/login"))
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .header(csrfToken.headerName(), csrfToken.token())
                        .POST(HttpRequest.BodyPublishers.ofString(loginJson(email)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpStatus.OK.value(), loginResponse.statusCode());

        HttpResponse<String> activeSessionResponse = getCurrentReader(client);
        assertEquals(HttpStatus.OK.value(), activeSessionResponse.statusCode());
        assertEquals(Long.toString(reader.getId()), activeSessionResponse.body());

        Thread.sleep(Duration.ofSeconds(2));

        HttpResponse<String> expiredSessionResponse = getCurrentReader(client);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), expiredSessionResponse.statusCode());
        assertTrue(expiredSessionResponse.body()
                .contains("\"code\":\"AUTHENTICATION_REQUIRED\""));
    }

    private HttpClient sessionClient() {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .build();
    }

    private IssuedCsrfToken issueCsrfToken(HttpClient client) throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(uri("/test/csrf-token"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(HttpStatus.OK.value(), response.statusCode());
        String[] csrfToken = response.body().split("\\n", 2);
        return new IssuedCsrfToken(csrfToken[0], csrfToken[1]);
    }

    private HttpResponse<String> getCurrentReader(HttpClient client) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri("/api/test/current-reader"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private String loginJson(String email) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, RAW_PASSWORD);
    }

    private record IssuedCsrfToken(String headerName, String token) {
    }

    @RestController
    public static class CsrfTokenController {

        @GetMapping(value = "/test/csrf-token", produces = MediaType.TEXT_PLAIN_VALUE)
        String csrfToken(HttpServletRequest request) {
            CsrfToken csrfToken =
                    (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            return csrfToken.getHeaderName() + "\n" + csrfToken.getToken();
        }
    }

    @RestController
    public static class CurrentReaderController {

        @GetMapping("/api/test/current-reader")
        long currentReader(
                @AuthenticationPrincipal AuthenticatedReader authenticatedReader,
                HttpSession session) {
            session.setMaxInactiveInterval(1);
            return authenticatedReader.readerId();
        }
    }
}
