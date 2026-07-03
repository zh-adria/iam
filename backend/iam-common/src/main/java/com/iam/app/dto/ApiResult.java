package com.iam.app.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ApiResult<T> {
    private boolean success;
    private String code;
    private String message;
    private T data;

    public static <T> ApiResult<T> ok(T data) { return ApiResult.<T>builder().success(true).code("OK").data(data).build(); }
    public static <T> ApiResult<T> ok(T data, String msg) { return ApiResult.<T>builder().success(true).code("OK").message(msg).data(data).build(); }
    public static <T> ApiResult<T> fail(String code, String msg) { return ApiResult.<T>builder().success(false).code(code).message(msg).build(); }
}
