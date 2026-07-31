package com.example.ilgeobolkka.global.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.config.SecurityConfig;
import com.example.ilgeobolkka.global.security.ApiSecurityErrorHandler;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = {
    CommonPageController.class,
    CommonPageControllerTest.TestAuthController.class
}, properties = "portone.payment.enabled=false")
@Import({
    SecurityConfig.class,
    ApiSecurityErrorHandler.class,
    CommonWebModelAdvice.class,
    CommonPageControllerTest.TestAuthController.class
})
class CommonPageControllerTest {

    private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; "
            + "script-src 'self'; "
            + "style-src 'self'; "
            + "img-src 'self' data:; "
            + "font-src 'self'; "
            + "connect-src 'self'; "
            + "object-src 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'; "
            + "frame-ancestors 'self'";

    private final MockMvc mockMvc;

    @Autowired
    CommonPageControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void 루트는_도서_탐색_화면으로_이동한다() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/books"));
    }

    @Test
    void 계약된_HTML_화면_경로를_same_origin으로_렌더링한다() throws Exception {
        for (String path : new String[] {
            "/books",
            "/books/1",
            "/signup",
            "/login"
        }) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk());
        }

        for (String path : new String[] {
            "/books/1/viewer",
            "/ink",
            "/ownership-payments",
            "/library"
        }) {
            mockMvc.perform(get(path).with(authentication(readerAuthentication())))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void 비로그인_공통_셸은_CSRF와_공개_내비게이션을_렌더링한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/books"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andReturn();

        CsrfToken csrfToken = csrfToken(result);
        String html = result.getResponse().getContentAsString();

        assertTrue(html.contains("name=\"_csrf\" content=\"" + csrfToken.getToken() + "\""));
        assertTrue(html.contains("name=\"_csrf_header\" content=\"" + csrfToken.getHeaderName() + "\""));
        assertTrue(html.contains("href=\"/books\""));
        assertTrue(html.contains("href=\"/signup\""));
        assertTrue(html.contains("href=\"/login\""));
        assertFalse(html.contains("href=\"/ink\""));
        assertFalse(html.contains("data-logout-form"));
        assertTrue(html.contains("/webjars/bootstrap/5.3.8/css/bootstrap.min.css"));
        assertTrue(html.contains("/js/common/shell.js"));
        assertFalse(html.contains("browser-sdk.esm.js"));
    }

    @Test
    void 로그인_공통_셸은_보호_내비게이션과_CSRF_hidden_field를_렌더링한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/library").with(authentication(readerAuthentication())))
                .andExpect(status().isOk())
                .andReturn();

        CsrfToken csrfToken = csrfToken(result);
        String html = result.getResponse().getContentAsString();

        assertTrue(html.contains("href=\"/ink\""));
        assertTrue(html.contains("href=\"/ownership-payments\""));
        assertTrue(html.contains("href=\"/library\""));
        assertTrue(html.contains("data-logout-form"));
        assertTrue(html.contains("name=\"" + csrfToken.getParameterName() + "\""));
        assertTrue(html.contains("value=\"" + csrfToken.getToken() + "\""));
        assertFalse(html.contains("href=\"/signup\""));
        assertFalse(html.contains("href=\"/login\""));
    }

    @Test
    void 결제_비활성화_환경의_잉크_화면은_구매를_막고_잔액과_원장을_렌더링한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/ink").with(authentication(readerAuthentication())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(header().string(
                        "Content-Security-Policy",
                        CONTENT_SECURITY_POLICY))
                .andReturn();

        String html = result.getResponse().getContentAsString();

        assertTrue(html.contains("data-ink-page"));
        assertTrue(html.contains("data-ink-balance"));
        assertTrue(html.contains("data-ink-purchase"));
        assertTrue(html.matches(
                "(?s).*data-ink-purchase[^>]*disabled=\"disabled\">구매 비활성화</button>.*"));
        assertTrue(html.contains("현재 환경에서는 잉크 구매를 사용할 수 없습니다."));
        assertTrue(html.contains("data-ink-payment-status"));
        assertTrue(html.contains("data-ink-ledger"));
        assertTrue(html.contains("data-ink-ledger-previous"));
        assertTrue(html.contains("data-ink-ledger-next"));
        assertTrue(html.contains("aria-label=\"잉크 내역 페이지\""));
        assertTrue(html.contains("/js/ink/ink.js"));
        assertFalse(html.contains("browser-sdk.esm.js"));
    }

    @Test
    void 결제_활성화_환경의_잉크_화면은_구매를_허용한다() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("portone.payment.enabled", "true");
        ExtendedModelMap model = new ExtendedModelMap();

        new CommonPageController(environment).ink(model);

        assertEquals(true, model.get("inkPurchaseEnabled"));
    }

    @Test
    void 운영_프로필의_잉크_화면은_활성화_설정이_있어도_구매를_막는다() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("portone.payment.enabled", "true");
        environment.setActiveProfiles("prod");
        ExtendedModelMap model = new ExtendedModelMap();

        new CommonPageController(environment).ink(model);

        assertEquals(false, model.get("inkPurchaseEnabled"));
    }

    @Test
    void 비로그인_사용자는_보호_HTML에서_저장된_요청_없이_로그인으로_이동한다() throws Exception {
        for (String path : new String[] {
            "/books/1/viewer",
            "/ink",
            "/ownership-payments",
            "/library"
        }) {
            MvcResult result = mockMvc.perform(get(path))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"))
                    .andReturn();

            assertNull(result.getRequest().getSession(false));
        }
    }

    @Test
    void 공통_셸은_차단_CSP와_공통_오류_영역을_제공한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/books"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", CONTENT_SECURITY_POLICY))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertTrue(html.contains("role=\"alert\""));
        assertTrue(html.contains("aria-live=\"assertive\""));
        assertTrue(html.contains("data-common-error"));
        assertFalse(CONTENT_SECURITY_POLICY.contains("'unsafe-inline'"));
        assertFalse(CONTENT_SECURITY_POLICY.contains("'unsafe-eval'"));
    }

    @Test
    void 렌더링된_CSRF_meta_토큰을_보낸_fetch만_상태_변경을_허용한다() throws Exception {
        MvcResult pageResult = mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) pageResult.getRequest().getSession(false);
        CsrfToken csrfToken = csrfToken(pageResult);
        assertNotNull(session);

        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .header(csrfToken.getHeaderName(), csrfToken.getToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_CSRF_TOKEN"));
    }

    @Test
    void 공통_정적_자산은_same_origin으로_제공된다() throws Exception {
        mockMvc.perform(get("/js/common/request-json.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("cdn.portone.io"))));

        mockMvc.perform(get("/js/common/error-display.js"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/js/common/shell.js"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/js/ink/ink.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("cdn.portone.io"))));

        mockMvc.perform(get("/js/ink/ink-page.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "https://cdn.portone.io/v2/browser-sdk.esm.js")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/cancel"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/refund"))));

        mockMvc.perform(get("/css/common.css"))
                .andExpect(status().isOk());
    }

    private CsrfToken csrfToken(MvcResult result) {
        return (CsrfToken) result.getRequest().getAttribute(CsrfToken.class.getName());
    }

    private Authentication readerAuthentication() {
        return new TestingAuthenticationToken(
                new AuthenticatedReader(42L),
                null,
                "ROLE_USER");
    }

    @RestController
    public static class TestAuthController {

        @PostMapping("/api/auth/login")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void login() {
        }
    }
}
