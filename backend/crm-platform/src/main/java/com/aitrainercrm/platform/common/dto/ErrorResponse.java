package com.aitrainercrm.platform.common.dto;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

/**
 * Standard error body for every 4xx/5xx response (see GlobalExceptionHandler).
 * {@code errorCode} is a stable machine-readable string (e.g.
 * "RESOURCE_NOT_FOUND") for client-side branching; {@code message} is
 * human-readable; {@code fieldErrors} is populated only for validation
 * failures (400s from @Valid), one entry per invalid field.
 */
@Builder
public record ErrorResponse(
        boolean success,
        String errorCode,
        String message,
        int status,
        String path,
        Instant timestamp,
        List<FieldError> fieldErrors,
        String traceId) {

    public record FieldError(String field, String message, Object rejectedValue) {
    }

    public static ErrorResponse of(String errorCode, String message, int status, String path, String traceId) {
        return ErrorResponse.builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .status(status)
                .path(path)
                .timestamp(Instant.now())
                .traceId(traceId)
                .build();
    }
}
