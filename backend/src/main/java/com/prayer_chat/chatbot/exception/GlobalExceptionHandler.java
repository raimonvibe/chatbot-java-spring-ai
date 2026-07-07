package com.prayer_chat.chatbot.exception;

import com.prayer_chat.chatbot.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handling for all REST controllers (chain-of-responsibility via Spring's
 * handler exception resolvers). Maps domain exceptions to consistent HTTP responses using
 * {@link ApiErrorResponse} so controllers do not need per-endpoint try/catch blocks.
 *
 * <p>Security note: 5xx responses never include exception messages (may contain internals);
 * 4xx responses include safe, intentional messages only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(ForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(ChatbotLimitReachedException.class)
    public ResponseEntity<ApiErrorResponse> handleChatbotLimit(ChatbotLimitReachedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiErrorResponse.of(ex.getMessage(), Map.of(
                "currentCount", ex.getCurrentCount(),
                "maxAllowed", ex.getMaxAllowed()
            )));
    }

    @ExceptionHandler(WebsiteTooLargeException.class)
    public ResponseEntity<Map<String, Object>> handleWebsiteTooLarge(WebsiteTooLargeException ex) {
        HttpStatus status = ex.isBillingEnabled() ? HttpStatus.PAYMENT_REQUIRED : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(ex.toPayload());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiErrorResponse> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiErrorResponse.of(ex.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of("Validation failed"));
    }

    /**
     * Spring Security must keep handling authorization/authentication failures
     * (401/403 with the configured entry point), so re-throw instead of mapping.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurityFrameworkExceptions(RuntimeException ex) {
        throw ex;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
            .body(ApiErrorResponse.of("Validation failed", fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        logger.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiErrorResponse.of("An unexpected error occurred. Please try again."));
    }
}
