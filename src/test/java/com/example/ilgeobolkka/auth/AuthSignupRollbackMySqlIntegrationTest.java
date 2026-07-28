package com.example.ilgeobolkka.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.ink.service.InkService;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Import(AuthSignupRollbackMySqlIntegrationTest.FailingInkServiceConfig.class)
class AuthSignupRollbackMySqlIntegrationTest {

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final InkService inkService;

    @Autowired
    AuthSignupRollbackMySqlIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            InkService inkService) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.inkService = inkService;
    }

    @Test
    void 잉크_계좌_생성에_실패하면_Reader_생성도_롤백한다() throws Exception {
        String email = "rollback-" + UUID.randomUUID() + "@example.com";
        doThrow(new IllegalStateException("강제 계좌 생성 실패"))
                .when(inkService)
                .createInkAccount(anyLong());

        mockMvc.perform(post("/api/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Valid-password1!"
                                }
                                """.formatted(email)))
                .andExpect(status().isInternalServerError());

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reader WHERE email = ?",
                        Integer.class,
                        email));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingInkServiceConfig {

        @Bean
        @Primary
        InkService failingInkService() {
            return mock(InkService.class);
        }
    }
}
