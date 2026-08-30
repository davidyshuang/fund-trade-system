package com.fund.trade.api;

/**
 * REST API 统一响应封装。
 * 成功：code = 0；失败：code 为业务错误码（如 40003）。
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "success", null);
    }
}
