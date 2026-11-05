package com.expensetracker.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Turns every error into the contract's JSON shape.
 * Extending ResponseEntityExceptionHandler covers the standard MVC errors (bad JSON, wrong
 * method, unknown URL, type mismatch, ResponseStatusException) so none of them become a 500.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // 400 with fieldErrors
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldErrorItem> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldErrorItem(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(status).headers(headers)
                .body(build(status, "Validation failed", request, fieldErrors));
    }

    // Everything the base class handles (ResponseStatusException, 404 URL, 405, bad JSON...)
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String message = (ex instanceof ErrorResponse er) ? er.getBody().getDetail() : null;
        return ResponseEntity.status(status).headers(headers)
                .body(build(status, message, request, null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "Operation conflicts with existing data", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, WebRequest request) {
        String msg = (ex instanceof BadCredentialsException) ? "Invalid email or password"
                                                              : "Authentication required";
        return respond(HttpStatus.UNAUTHORIZED, msg, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex, WebRequest request) {
        return respond(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
    }

    // ---- helpers ----
    private ResponseEntity<ApiError> respond(HttpStatus status, String message, WebRequest request) {
        return ResponseEntity.status(status).body(build(status, message, request, null));
    }

    private ApiError build(HttpStatusCode status, String message, WebRequest request,
                           List<ApiError.FieldErrorItem> fieldErrors) {
        HttpStatus hs = HttpStatus.resolve(status.value());
        String error = hs != null ? hs.getReasonPhrase() : "Error";
        String msg = (message == null || message.isBlank()) ? error : message;
        String path = (request instanceof ServletWebRequest swr) ? swr.getRequest().getRequestURI() : "";
        return new ApiError(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                status.value(), error, msg, path, fieldErrors);
    }
}
