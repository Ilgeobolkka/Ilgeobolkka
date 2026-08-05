package com.example.ilgeobolkka.global.logging;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ServletException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(OutputCaptureExtension.class)
class ApiRequestLoggingFilterTest {

    private final ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter();

    @Test
    void API_요청은_서버_발급_ID와_비민감_필드만_로그에_남긴다(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/books");
        request.setQueryString("password=query-secret");
        request.addHeader("Cookie", "JSESSIONID=session-secret");
        request.addHeader("X-Viewer-Session-Id", "viewer-secret");
        request.addHeader("X-PortOne-Secret", "portone-secret");
        request.addHeader("X-Internal-Storage-Path", "/private/books/source.pdf");
        request.setContent("DB_PASSWORD=db-secret".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertDoesNotThrow(() -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                response.setStatus(204)));

        String requestId = response.getHeader(ApiRequestLoggingFilter.REQUEST_ID_HEADER);
        assertNotNull(requestId);
        assertDoesNotThrow(() -> UUID.fromString(requestId));
        assertTrue(output.getOut().contains("requestId=" + requestId));
        assertTrue(output.getOut().contains("method=GET"));
        assertTrue(output.getOut().contains("path=/api/books"));
        assertTrue(output.getOut().contains("status=204"));
        assertFalse(output.getOut().contains("query-secret"));
        assertFalse(output.getOut().contains("session-secret"));
        assertFalse(output.getOut().contains("viewer-secret"));
        assertFalse(output.getOut().contains("portone-secret"));
        assertFalse(output.getOut().contains("/private/books/source.pdf"));
        assertFalse(output.getOut().contains("db-secret"));
        assertNull(MDC.get(ApiRequestLoggingFilter.REQUEST_ID_MDC_KEY));
    }

    @Test
    void API_처리_예외는_원문_대신_예외_타입만_로그에_남긴다(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhooks/portone");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
                ServletException.class,
                () -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
                    throw new ServletException("PORTONE_API_SECRET=exposed");
                }));

        assertTrue(output.getOut().contains("exceptionType=ServletException"));
        assertFalse(output.getOut().contains("PORTONE_API_SECRET=exposed"));
        assertNull(MDC.get(ApiRequestLoggingFilter.REQUEST_ID_MDC_KEY));
    }

    @Test
    void API가_아닌_요청은_추적_로그를_남기지_않는다(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/app.css");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertDoesNotThrow(() -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                response.setStatus(200)));

        assertNull(response.getHeader(ApiRequestLoggingFilter.REQUEST_ID_HEADER));
        assertFalse(output.getOut().contains("API 요청"));
    }
}
