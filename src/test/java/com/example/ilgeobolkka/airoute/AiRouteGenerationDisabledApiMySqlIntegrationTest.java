package com.example.ilgeobolkka.airoute;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/** 기능을 끈 일반 서버에는 생성·조회 handler가 등록되지 않고 기존 API는 유지되는지 검증한다. */
@SpringBootTest(properties = "ai-route.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class AiRouteGenerationDisabledApiMySqlIntegrationTest {

    private final MockMvc mockMvc;

    @Autowired
    AiRouteGenerationDisabledApiMySqlIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void 비활성_서버는_생성_조회가_404이고_기존_도서_API는_동작한다() throws Exception {
        TestingAuthenticationToken reader = new TestingAuthenticationToken(
                new AuthenticatedReader(470_901L), null, "ROLE_USER");

        mockMvc.perform(post("/api/books/1/ai-route-generations")
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"목적\",\"maxAdditionalInk\":1,\"depth\":null}")
                        .with(authentication(reader))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/ai-route-generations/{generationId}", UUID.randomUUID())
                        .with(authentication(reader)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/books").param("page", "1"))
                .andExpect(status().isOk());
    }
}
