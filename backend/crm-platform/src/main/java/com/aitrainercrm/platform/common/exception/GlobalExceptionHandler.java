package com.aitrainercrm.platform.common.exception;

import com.aitrainercrm.platform.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Single place every exception in the application funnels through before it
 * becomes an HTTP response. Two goals: never leak a raw stack trace or
 * internal exception message to a client, and always return the same
 * {@link ErrorResponse} shape so the frontend/SDKs have exactly one error
 * format to handle.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Business exception [{}] {}: {}", traceId, ex.getErrorCode(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(
                ex.getErrorCode(), ex.getMessage(), ex.getStatus().value(), request.getRequestURI(), traceId);
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
                .toList();

        ErrorResponse body = ErrorResponse.builder()
                .success(false)
                .errorCode("VALIDATION_FAILED")
                .message("One or more fields failed validation")
                .status(HttpStatus.BAD_REQUEST.value())
                .path(request.getRequestURI())
                .timestamp(java.time.Instant.now())
                .fieldErrors(fieldErrors)
                .traceId(traceId)
                .build();
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        ErrorResponse body = ErrorResponse.of(
                "INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED.value(),
                request.getRequestURI(), traceId);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        ErrorResponse body = ErrorResponse.of(
                "FORBIDDEN", "You do not have permission to perform this action", HttpStatus.FORBIDDEN.value(),
                request.getRequestURI(), traceId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(HttpServletRequest request, DataIntegrityViolationException ex) {
        String traceId = UUID.randomUUID().toString();
        // The raw exception (constraint name, SQL) is logged, never returned -
        // callers only ever see a generic conflict message.
        log.warn("Data integrity violation [{}]: {}", traceId, ex.getMostSpecificCause().getMessage());
        ErrorResponse body = ErrorResponse.of(
                "DATA_INTEGRITY_VIOLATION", "The request conflicts with existing data", HttpStatus.CONFLICT.value(),
                request.getRequestURI(), traceId);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Spring's own multipart parser rejects an oversized upload before AttachmentService#create
     * ever runs (spring.servlet.multipart.max-file-size in application.yml), so
     * AttachmentService's own MAX_FILE_SIZE_BYTES check never actually fires for that case in
     * practice - it exists as a second line of defense (and a clearer error message) for a
     * client that sends the size in a request body field the parser itself doesn't inspect.
     * Without this handler, MaxUploadSizeExceededException would otherwise fall through to
     * handleUnexpected and return a misleading 500.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        ErrorResponse body = ErrorResponse.of(
                "FILE_TOO_LARGE", "The uploaded file exceeds the maximum allowed size", HttpStatus.PAYLOAD_TOO_LARGE.value(),
                request.getRequestURI(), traceId);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Unhandled exception [{}] on {}", traceId, request.getRequestURI(), ex);
        ErrorResponse body = ErrorResponse.of(
                "INTERNAL_ERROR", "An unexpected error occurred. Reference: " + traceId,
                HttpStatus.INTERNAL_SERVER_ERROR.value(), request.getRequestURI(), traceId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
