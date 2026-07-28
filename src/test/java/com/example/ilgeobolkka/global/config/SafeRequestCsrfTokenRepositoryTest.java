package com.example.ilgeobolkka.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;

class SafeRequestCsrfTokenRepositoryTest {

    private final SafeRequestCsrfTokenRepository repository = new SafeRequestCsrfTokenRepository();

    @Test
    void 세션이_없는_상태_변경_요청은_CSRF_토큰_저장으로_세션을_만들지_않는다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        CsrfToken token = repository.generateToken(request);

        repository.saveToken(token, request, new MockHttpServletResponse());

        assertNull(request.getSession(false));
        assertNull(repository.loadToken(request));
    }

    @Test
    void 안전한_요청은_CSRF_토큰을_세션에_저장한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        CsrfToken token = repository.generateToken(request);

        repository.saveToken(token, request, new MockHttpServletResponse());

        assertNotNull(request.getSession(false));
        assertEquals(token.getToken(), repository.loadToken(request).getToken());
    }
}
