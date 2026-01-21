package com.example.demo.bean;

import lombok.Data;

/**
 * 响应结果类
 *
 * @param <T> 任意类型
 */
@Data
public class Response<T> {

    /**
     * 响应状态码，200是正常，非200表示异常
     */
    private int status;
    /**
     * 异常编号
     */
    private String code;
    /**
     * 异常信息
     */
    private String message;
    /**
     * 响应数据
     */
    private T data;

    public static <T> Response<T> success() {
        return success(200, null, null);
    }

    public static <T> Response<T> success(T data) {
        return success(200, null, data);
    }

    public static <T> Response<T> fail(String message) {
        return fail(5001, null, message, null);
    }

    public static <T> Response<T> fail(String errorCode, String message) {
        return fail(5001, errorCode, message, null);
    }

    public static <T> Response<T> success(int status, String message, T data) {
        Response<T> r = new Response<>();
        r.setStatus(status);
        r.setMessage(message);
        r.setData(data);

        return r;
    }

    public static <T> Response<T> fail(int status, String errorCode, String message) {
        return fail(status, errorCode, message, null);
    }

    public static <T> Response<T> fail(int status, String errorCode, String message, T data) {
        Response<T> r = new Response<>();
        r.setStatus(status);
        r.setCode(errorCode);
        r.setMessage(message);
        r.setData(data);
        return r;
    }
}
