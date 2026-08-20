package com.example.ilgeobolkka.airoute.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.config.SecurityConfig;
import com.example.ilgeobolkka.global.exception.GlobalExceptionHandler;
import com.example.ilgeobolkka.global.security.ApiSecurityErrorHandler;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.global.web.CommonWebModelAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(
        controllers = AiRouteGenerationPageController.class,
        properties = "ai-route.enabled=true")
@ContextConfiguration(classes = {
    AiRouteGenerationPageController.class,
    CommonWebModelAdvice.class,
    SecurityConfig.class,
    ApiSecurityErrorHandler.class,
    GlobalExceptionHandler.class
})
class AiRouteGenerationPageTest {

    private final MockMvc mockMvc;

    @Autowired
    AiRouteGenerationPageTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void 로그인하지_않으면_저장된_요청_없이_로그인으로_이동한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/books/17/ai-route"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andReturn();

        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void 로그인_독자에게_bookId_CSRF_입력_미리보기_저장_화면을_렌더링한다()
            throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/books/17/ai-route").with(authentication(readerAuthentication())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andReturn();

        CsrfToken csrfToken =
                (CsrfToken) result.getRequest().getAttribute(CsrfToken.class.getName());
        String html = result.getResponse().getContentAsString();

        assertTrue(html.contains("data-ai-route-generation-root"));
        assertTrue(html.contains("data-book-id=\"17\""));
        assertTrue(html.contains("name=\"_csrf\" content=\"" + csrfToken.getToken() + "\""));
        assertTrue(html.contains("name=\"_csrf_header\" content=\""
                + csrfToken.getHeaderName() + "\""));
        assertTrue(html.contains("data-ai-route-purpose"));
        assertTrue(html.contains("data-ai-route-budget-fields"));
        assertTrue(html.contains("data-ai-route-depth-fields"));
        assertTrue(html.contains("value=\"QUICK\""));
        assertTrue(html.contains("최소 경로 · 최대 5페이지"));
        assertTrue(html.contains("value=\"BALANCED\""));
        assertTrue(html.contains("적정 경로 · 최대 10페이지"));
        assertTrue(html.contains("value=\"DEEP\""));
        assertTrue(html.contains("자세한 경로 · 최대 15페이지"));
        assertFalse(html.contains("빠른 · 최대 5페이지"));
        assertFalse(html.contains("균형 · 최대 10페이지"));
        assertFalse(html.contains("깊이 · 최대 15페이지"));
        assertTrue(html.contains("data-ai-route-preview"));
        assertTrue(html.contains("data-ai-route-item-template"));
        assertTrue(html.contains("data-ai-route-save"));
        assertTrue(html.contains("/js/ai-route/generation.js"));
        assertFalse(html.contains("OPENAI_API_KEY"));
        assertFalse(html.contains("analysisText"));
    }

    @Test
    void 생성_모듈은_기존_API와_멱등키를_사용하고_문자열을_HTML로_삽입하지_않는다()
            throws Exception {
        String javascript = mockMvc.perform(get("/js/ai-route/generation-page.js"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(javascript.contains("/api/books/"));
        assertTrue(javascript.contains("/api/ink/balance"));
        assertTrue(javascript.contains("/ai-route-generations"));
        assertTrue(javascript.contains("Idempotency-Key"));
        assertTrue(javascript.contains("randomUUID"));
        assertTrue(javascript.contains("AI_ROUTE_ENTITLEMENT_CHANGED"));
        assertTrue(javascript.contains("textContent"));
        assertTrue(javascript.contains("disabled"));
        assertFalse(javascript.contains("innerHTML"));
        assertFalse(javascript.contains("insertAdjacentHTML"));
        assertFalse(javascript.contains("https://"));
    }

    @Test
    void feature_flag가_false면_PageController_Bean을_등록하지_않는다() {
        new ApplicationContextRunner()
                .withUserConfiguration(AiRouteGenerationPageController.class)
                .withPropertyValues("ai-route.enabled=false")
                .run(context -> assertFalse(
                        context.containsBean("aiRouteGenerationPageController")));
    }

    private Authentication readerAuthentication() {
        return new TestingAuthenticationToken(
                new AuthenticatedReader(42L),
                null,
                "ROLE_USER");
    }
}
