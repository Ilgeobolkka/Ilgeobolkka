package com.example.ilgeobolkka.global.exception;

import com.example.ilgeobolkka.auth.exception.InvalidCredentialsException;
import com.example.ilgeobolkka.book.exception.BookNotFoundException;
import com.example.ilgeobolkka.book.exception.BookPageNotFoundException;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentUnavailableException;
import com.example.ilgeobolkka.infra.portone.PortOneWebhookVerificationException;
import com.example.ilgeobolkka.ink.exception.InkPurchaseNotFoundException;
import com.example.ilgeobolkka.ink.exception.InsufficientInkException;
import com.example.ilgeobolkka.ink.exception.PaymentStateConflictException;
import com.example.ilgeobolkka.ink.exception.PaymentVerificationException;
import com.example.ilgeobolkka.ownership.exception.BookAlreadyOwnedException;
import com.example.ilgeobolkka.reader.exception.EmailAlreadyExistsException;
import com.example.ilgeobolkka.reading.exception.PageContentNotFoundException;
import com.example.ilgeobolkka.reading.exception.ReadingSessionNotFoundException;
import com.example.ilgeobolkka.reading.exception.ViewerSessionReplacedException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.ilgeobolkka.global.logging.ApiRequestLoggingFilter;

@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        MethodArgumentTypeMismatchException.class,
        ServletRequestBindingException.class,
        HttpMessageNotReadableException.class,
        HttpMediaTypeNotSupportedException.class
    })
    ResponseEntity<ApiErrorResponse> handleInvalidInput(Exception exception) {
        logFailure(ErrorCode.INVALID_INPUT, exception);
        return response(ErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(PortOneWebhookVerificationException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidWebhookSignature(
            PortOneWebhookVerificationException exception) {
        logFailure(ErrorCode.INVALID_WEBHOOK_SIGNATURE, exception);
        return response(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        logFailure(ErrorCode.ACCESS_DENIED, exception);
        return response(ErrorCode.ACCESS_DENIED);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    ResponseEntity<ApiErrorResponse> handleEmailAlreadyExists(
            EmailAlreadyExistsException exception) {
        logFailure(ErrorCode.EMAIL_ALREADY_EXISTS, exception);
        return response(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception) {
        logFailure(ErrorCode.INVALID_CREDENTIALS, exception);
        return response(ErrorCode.INVALID_CREDENTIALS);
    }

    @ExceptionHandler(BookAlreadyOwnedException.class)
    ResponseEntity<ApiErrorResponse> handleBookAlreadyOwned(
            BookAlreadyOwnedException exception) {
        logFailure(ErrorCode.BOOK_ALREADY_OWNED, exception);
        return response(ErrorCode.BOOK_ALREADY_OWNED);
    }

    @ExceptionHandler(ViewerSessionReplacedException.class)
    ResponseEntity<ApiErrorResponse> handleViewerSessionReplaced(
            ViewerSessionReplacedException exception) {
        logFailure(ErrorCode.VIEWER_SESSION_REPLACED, exception);
        return response(ErrorCode.VIEWER_SESSION_REPLACED);
    }

    @ExceptionHandler({
        BookNotFoundException.class,
        BookPageNotFoundException.class,
        InkPurchaseNotFoundException.class,
        PageContentNotFoundException.class,
        ReadingSessionNotFoundException.class
    })
    ResponseEntity<ApiErrorResponse> handleResourceNotFound(Exception exception) {
        logFailure(ErrorCode.RESOURCE_NOT_FOUND, exception);
        return response(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(InsufficientInkException.class)
    ResponseEntity<ApiErrorResponse> handleInsufficientInk(InsufficientInkException exception) {
        logFailure(ErrorCode.INSUFFICIENT_INK, exception);
        return response(ErrorCode.INSUFFICIENT_INK);
    }

    @ExceptionHandler(PaymentStateConflictException.class)
    ResponseEntity<ApiErrorResponse> handlePaymentStateConflict(
            PaymentStateConflictException exception) {
        logFailure(ErrorCode.PAYMENT_STATE_CONFLICT, exception);
        return response(ErrorCode.PAYMENT_STATE_CONFLICT);
    }

    @ExceptionHandler(PaymentVerificationException.class)
    ResponseEntity<ApiErrorResponse> handlePaymentVerification(
            PaymentVerificationException exception) {
        logFailure(exception.errorCode(), exception);
        return response(exception.errorCode());
    }

    @ExceptionHandler(PortOnePaymentUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handlePaymentProviderUnavailable(
            PortOnePaymentUnavailableException exception) {
        logFailure(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, exception);
        return response(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        logFailure(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        return response(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private void logFailure(ErrorCode errorCode, Exception exception) {
        String requestId = MDC.get(ApiRequestLoggingFilter.REQUEST_ID_MDC_KEY);
        log.warn(
                "API 요청 실패 requestId={} errorCode={} exceptionType={}",
                requestId == null ? "-" : requestId,
                errorCode,
                exception.getClass().getSimpleName());
    }

    private ResponseEntity<ApiErrorResponse> response(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.status())
                .body(ApiErrorResponse.from(errorCode));
    }
}
