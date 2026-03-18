package com.datalake.platform.common.web;

import java.time.Instant;

public record ApiResponse<T>(
    int code,
    String message,
    T data,
    Instant timestamp,
    String requestId
) {
    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(0, "OK", data, Instant.now(), requestId);
    }

    public static ApiResponse<Void> success(String requestId) {
        return new ApiResponse<>(0, "OK", null, Instant.now(), requestId);
    }

    public static <T> ApiResponse<T> failure(int code, String message, String requestId) {
        return new ApiResponse<>(code, message, null, Instant.now(), requestId);
    }
}

