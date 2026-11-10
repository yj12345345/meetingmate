package com.meetingmate.app.exception;

import com.meetingmate.app.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Global error mapping.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so every standard Spring MVC
 * exception (unknown path, wrong method, unreadable body, unsupported media type,
 * missing parameter, ...) keeps its correct 4xx status. Previously a catch-all
 * {@code @ExceptionHandler(Exception.class)} turned all of them into 500.
 * We only override how the body is rendered so clients keep the ApiResponse shape.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ---- Spring MVC standard exceptions: keep status, render as ApiResponse ----

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String code = status != null ? status.name() : "HTTP_" + statusCode.value();
        if (statusCode.is5xxServerError()) {
            log.error("Unhandled framework exception -> {}", statusCode.value(), ex);
        } else {
            // Client errors are expected traffic; keep them at DEBUG to avoid log noise.
            log.debug("Client error {}: {}", statusCode.value(), ex.getMessage());
        }
        return ResponseEntity.status(statusCode).headers(headers)
                .body(ApiResponse.fail(code, clientMessage(statusCode)));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        if (message.isBlank()) {
            message = "요청 값이 올바르지 않습니다.";
        }
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", message));
    }

    private static String clientMessage(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "요청 형식이 올바르지 않습니다.";
            case 404 -> "요청한 리소스를 찾을 수 없습니다.";
            case 405 -> "지원하지 않는 HTTP 메서드입니다.";
            case 406 -> "지원하지 않는 응답 형식입니다.";
            case 415 -> "지원하지 않는 Content-Type입니다.";
            default -> status.is5xxServerError() ? "서버 내부 오류가 발생했습니다." : "요청을 처리할 수 없습니다.";
        };
    }

    // ---- Domain / security exceptions ----

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleSpringAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("ACCESS_DENIED", "접근 권한이 없습니다."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("UNAUTHORIZED", "인증이 필요합니다."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail("ILLEGAL_STATE", e.getMessage()));
    }

    // ---- Dependency outage: database unreachable -> 503, not a bug (500) ----

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<String> handleDatabaseUnavailable(Exception e) {
        // WARN without stack trace: during an outage every request hits this, and the
        // cause is already visible in Hikari / health metrics.
        log.warn("Database unavailable: {}", e.toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(DatabaseUnavailable.RETRY_AFTER_SECONDS))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(DatabaseUnavailable.BODY);
    }

    // ---- Everything else is a real server error: log it with the request ID ----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        // The log pattern adds [requestId] from MDC, so this line can be matched to the
        // client's X-Request-Id. Previously this handler logged nothing at all.
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
