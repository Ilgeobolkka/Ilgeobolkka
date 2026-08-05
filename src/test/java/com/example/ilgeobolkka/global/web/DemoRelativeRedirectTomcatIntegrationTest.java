package com.example.ilgeobolkka.global.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "DEMO_VALIDATION_PASSWORD=")
@ActiveProfiles({"test", "demo"})
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class DemoRelativeRedirectTomcatIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void demo_루트_리다이렉트는_상대_경로를_사용한다() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/"))
                .GET()
                .build();

        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        String location = response.headers()
                .firstValue("Location")
                .orElseThrow(() -> new AssertionError("Location 헤더가 없습니다."));

        assertTrue(response.statusCode() >= 300 && response.statusCode() < 400);
        assertEquals("/books", location);
        assertFalse(location.contains("origin.demo.0younge.click"));
        assertFalse(location.contains("http://"));
    }
}
