package com.example.ilgeobolkka.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

import jakarta.servlet.ServletContext;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "prod"})
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(ProdSessionCookieIntegrationTest.SessionController.class)
class ProdSessionCookieIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ServletContext servletContext;

    @Test
    void 세션_추적은_쿠키만_사용한다() {
        assertEquals(Set.of(SessionTrackingMode.COOKIE), servletContext.getEffectiveSessionTrackingModes());
    }

    @Test
    void 운영_프로필의_세션_쿠키는_보안_속성을_모두_포함한다() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/test/session-cookie"))
                .GET()
                .build();

        HttpResponse<Void> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding());

        String setCookie = response.headers()
                .firstValue("Set-Cookie")
                .orElseThrow(() -> new AssertionError("세션 쿠키가 발급되지 않았습니다."));

        assertEquals(HttpStatus.NO_CONTENT.value(), response.statusCode());
        assertTrue(setCookie.contains("Path=/"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        assertTrue(setCookie.contains("Secure"));
    }

    @RestController
    public static class SessionController {

        @GetMapping("/test/session-cookie")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void createSession(HttpServletRequest request) {
            request.getSession(true);
        }
    }
}
