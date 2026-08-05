package com.example.ilgeobolkka.global.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import com.example.ilgeobolkka.global.exception.ApiErrorResponse;
import com.example.ilgeobolkka.global.exception.ErrorCode;
import com.example.ilgeobolkka.global.logging.ApiRequestLoggingFilter;

@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiSecurityErrorHandler.class);

    private final ObjectMapper objectMapper;

    public ApiSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException {
        write(response, ErrorCode.AUTHENTICATION_REQUIRED, authenticationException);
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ErrorCode errorCode = accessDeniedException instanceof CsrfException
                ? ErrorCode.INVALID_CSRF_TOKEN
                : ErrorCode.ACCESS_DENIED;
        write(response, errorCode, accessDeniedException);
    }

    private void write(HttpServletResponse response, ErrorCode errorCode, Exception exception) throws IOException {
        String requestId = MDC.get(ApiRequestLoggingFilter.REQUEST_ID_MDC_KEY);
        log.warn(
                "API 보안 요청 실패 requestId={} errorCode={} exceptionType={}",
                requestId == null ? "-" : requestId,
                errorCode,
                exception.getClass().getSimpleName());
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.from(errorCode));
    }
}
