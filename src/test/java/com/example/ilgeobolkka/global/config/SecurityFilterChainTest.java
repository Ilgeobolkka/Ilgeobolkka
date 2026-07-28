package com.example.ilgeobolkka.global.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ilgeobolkka.global.logging.ApiRequestLoggingFilter;
import com.example.ilgeobolkka.global.security.ApiSecurityErrorHandler;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.global.smoke.SmokeController;

@WebMvcTest(controllers = {
    SmokeController.class,
    SecurityFilterChainTest.TestController.class
})
@Import({
    SecurityConfig.class,
    ApiSecurityErrorHandler.class,
    SmokeController.class,
    SecurityFilterChainTest.TestController.class
})
@ExtendWith(OutputCaptureExtension.class)
class SecurityFilterChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 도서_목록과_상세_조회만_익명_사용자에게_공개한다() throws Exception {
        mockMvc.perform(get("/api/books").param("page", "1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/books/1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/books/1/pages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void smoke_요청은_민감정보_없이_익명으로_성공한다() throws Exception {
        mockMvc.perform(get("/api/smoke"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 보호_API의_익명_요청은_공통_인증_오류로_응답한다() throws Exception {
        mockMvc.perform(get("/api/ink/balance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Test
    void 커스텀_Authentication의_AuthenticatedReader를_컨트롤러에_전달한다() throws Exception {
        AuthenticatedReader authenticatedReader = new AuthenticatedReader(42L);
        Authentication authentication =
                new TestingAuthenticationToken(authenticatedReader, null, "ROLE_USER");

        assertSame(authenticatedReader, authentication.getPrincipal());
        mockMvc.perform(get("/api/test/authenticated-reader").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));
    }

    @Test
    void 보안_실패는_응답_요청_ID와_로그를_연결한다(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ink/balance"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String requestId = result.getResponse().getHeader(ApiRequestLoggingFilter.REQUEST_ID_HEADER);
        assertNotNull(requestId);
        assertTrue(output.getOut().contains("requestId=" + requestId));
        assertTrue(output.getOut().contains("errorCode=AUTHENTICATION_REQUIRED"));
    }

    @Test
    void 합성_이미지_fixture는_인증된_요청에도_정적_자산으로_공개하지_않는다() throws Exception {
        mockMvc.perform(get("/demo/book-pages/category-01.png").with(user("reader")))
                .andExpect(status().isNotFound());
    }

    @Test
    void 회원가입과_로그인은_익명_요청이어도_CSRF_토큰이_필요하다() throws Exception {
        mockMvc.perform(post("/api/auth/signup"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));

        mockMvc.perform(post("/api/auth/signup").with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/login").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void 보호된_상태_변경_API는_인증과_CSRF_토큰이_모두_필요하다() throws Exception {
        mockMvc.perform(post("/api/ink/purchases").with(user("reader")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));

        mockMvc.perform(post("/api/ink/purchases")
                        .with(user("reader"))
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));

        mockMvc.perform(post("/api/ink/purchases")
                        .with(user("reader"))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void 로그아웃은_인증과_CSRF_토큰이_모두_필요하다() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(post("/api/auth/logout")
                        .with(user("reader"))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void PortOne_웹훅_POST만_CSRF와_로그인_예외로_둔다() throws Exception {
        mockMvc.perform(post("/api/webhooks/portone"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/webhooks/portone/other"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));

        mockMvc.perform(get("/api/webhooks/portone"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @RestController
    public static class TestController {

        @GetMapping("/api/test/authenticated-reader")
        long authenticatedReader(@AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
            return authenticatedReader.readerId();
        }

        @GetMapping({
            "/api/books",
            "/api/books/1",
            "/api/books/1/pages",
            "/api/ink/balance",
            "/api/webhooks/portone"
        })
        void read() {
        }

        @PostMapping({
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/logout",
            "/api/ink/purchases",
            "/api/webhooks/portone",
            "/api/webhooks/portone/other"
        })
        void change() {
        }
    }
}
