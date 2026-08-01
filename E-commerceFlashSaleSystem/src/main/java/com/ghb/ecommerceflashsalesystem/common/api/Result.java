package com.ghb.ecommerceflashsalesystem.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.UUID;

/**
 * 统一响应体
 *
 * <p>所有接口统一返回该结构，保证前后端解析一致。通常不直接 new，而是通过
 * 静态工厂方法 {@link #success()} / {@link #success(Object)} / {@link #fail(ResultCode)}
 * 构造。</p>
 *
 * <ul>
 *     <li>{@code code}：业务状态码，0 表示成功，非 0 表示失败（见 {@link ResultCode}）</li>
 *     <li>{@code message}：提示消息</li>
 *     <li>{@code data}：业务数据，失败时为空（配合 {@code @JsonInclude(NON_NULL)}，空字段不序列化）</li>
 *     <li>{@code requestId}：请求追踪编号，用于日志链路追踪</li>
 *     <li>{@code timestamp}：响应时间戳（毫秒）</li>
 * </ul>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {
    /**
     * 状态码：0 表示成功，非 0 表示失败
     */
    private int code;
    /**
     * 响应消息
     */
    private String message;
    /**
     * 响应数据（成功时返回）
     */
    private T data;
    /**
     * 请求追踪编号，用于链路追踪
     */
    private String requestId;
    /**
     * 响应时间戳（毫秒）
     */
    private Long timestamp;

    // ---------- 私有构造，禁止外部直接 new ----------
    private Result() {
        this.requestId = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }

    // ---------- 静态工厂方法 ----------
    /**
     * 成功响应(无数据)
     */
    public static <T> Result<T> success() {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getDescription());
        return result;
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getDescription());
        result.setData(data);
        return result;
    }

    /**
     * 失败响应（使用 ResultCode 枚举）
     */
    public static <T> Result<T> fail(ResultCode resultCode) {
        Result<T> result = new Result<>();
        result.setCode(resultCode.getCode());
        result.setMessage(resultCode.getDescription());
        return result;
    }

    /**
     * 失败响应（自定义消息）
     */
    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        Result<T> result = new Result<>();
        result.setCode(resultCode.getCode());
        result.setMessage(message);
        return result;
    }
}
