package com.campusfire.common.api;

import java.time.Instant;

public class ApiResponse<T> {

    private final String code;
    private final String message;
    private final T data;
    private final Instant serverTime;

    private ApiResponse(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.serverTime = Instant.now();
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<T>("OK", "操作成功", data);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<T>(code, message, null);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public Instant getServerTime() {
        return serverTime;
    }
}

