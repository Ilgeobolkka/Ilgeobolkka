package com.example.ilgeobolkka.global.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class ApiSecurityErrorHandlerTest {

    private final ApiSecurityErrorHandler securityErrorHandler =
            new ApiSecurityErrorHandler(new ObjectMapper());

    @Test
    void 미인증_요청은_공통_인증_오류로_응답한다(CapturedOutput output) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityErrorHandler.commence(
                new MockHttpServletRequest(),
                response,
                new InsufficientAuthenticationException("JSESSIONID=exposed"));

        assertEquals(401, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        assertEquals(
                "{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"로그인이 필요합니다.\"}",
                response.getContentAsString());
        assertTrue(output.getOut().contains("errorCode=AUTHENTICATION_REQUIRED"));
        assertFalse(output.getOut().contains("JSESSIONID=exposed"));
    }

    @Test
    void CSRF_실패는_공통_CSRF_오류로_응답한다(CapturedOutput output) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityErrorHandler.handle(
                new MockHttpServletRequest(),
                response,
                new MissingCsrfTokenException(null));

        assertEquals(403, response.getStatus());
        assertEquals(
                "{\"code\":\"INVALID_CSRF_TOKEN\",\"message\":\"CSRF 토큰이 유효하지 않습니다.\"}",
                response.getContentAsString());
        assertTrue(output.getOut().contains("errorCode=INVALID_CSRF_TOKEN"));
    }

    @Test
    void 인가_실패는_공통_접근_오류로_응답한다(CapturedOutput output) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityErrorHandler.handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException("viewerSessionId=exposed"));

        assertEquals(403, response.getStatus());
        assertEquals(
                "{\"code\":\"ACCESS_DENIED\",\"message\":\"접근 권한이 없습니다.\"}",
                response.getContentAsString());
        assertTrue(output.getOut().contains("errorCode=ACCESS_DENIED"));
        assertFalse(output.getOut().contains("viewerSessionId=exposed"));
    }
}
