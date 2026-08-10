package com.aitrainercrm.platform.common.dto;

import java.time.Instant;
import lombok.Builder;

/**
 * Uniform envelope for every successful API response, so frontend/SDK code
 * can rely on one shape ({@code success}, {@code data}, {@code message},
 * {@code timestamp}) regardless of which endpoint it's calling. Error
 * responses use {@link ErrorResponse} instead - two distinct shapes, never
 * a partially-null hybrid of both.
 */
@Builder
public record ApiResponse<T>(boolean success, T data, String message, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().success(true).data(data).timestamp(Instant.now()).build();
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder().success(true).data(data).message(message).timestamp(Instant.now()).build();
    }
}
