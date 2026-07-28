package com.example.ilgeobolkka.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(AnonymousApiSessionIntegrationTest.ProtectedApiController.class)
class AnonymousApiSessionIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void 익명_보호_API_GET_요청은_세션과_쿠키를_생성하지_않는다() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/test/protected-resource"))
                .GET()
                .build();

        for (int attempt = 0; attempt < 2; attempt++) {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

            assertEquals(HttpStatus.UNAUTHORIZED.value(), response.statusCode());
            assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
        }
    }

    @Test
    void 익명_보호_API_POST_요청은_세션과_쿠키를_생성하지_않는다() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/test/protected-resource"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        for (int attempt = 0; attempt < 2; attempt++) {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

            assertEquals(HttpStatus.FORBIDDEN.value(), response.statusCode());
            assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
        }
    }

    @RestController
    public static class ProtectedApiController {

        @GetMapping("/api/test/protected-resource")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void protectedResource() {
        }

        @PostMapping("/api/test/protected-resource")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void changeProtectedResource() {
        }
    }
}
