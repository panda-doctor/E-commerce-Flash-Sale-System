package com.ghb.ecommerceflashsalesystem.common.api;

import lombok.Getter;

/**
 * 响应状态码枚举
 * 沿用 Day 2 约定：code + description
 */
@Getter
public enum ResultCode {
    SUCCESS(0, "成功"),
    PARAM_ERROR(40001, "参数错误"),
    NOT_FOUND(40004, "资源未找到"),
    DUPLICATE_PURCHASE(40901, "重复秒杀"),
    OUT_OF_STOCK(40902, "库存不足"),
    RATE_LIMITED(42900, "请求过于频繁"),
    SYSTEM_ERROR(50000, "系统错误"),
    /** AI 客服：外部大模型服务未配置（ai.llm.api-key 等缺失） */
    AI_SERVICE_UNCONFIGURED(50300, "AI 服务未配置"),
    /** AI 客服：外部大模型调用失败（网络/超时/非 2xx/响应异常） */
    AI_SERVICE_ERROR(50301, "AI 服务调用失败");

    private final int code;
    private final String description;

    ResultCode(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ResultCode fromValue(int code){
        for(ResultCode rc : values()) {
            if(rc.getCode() == code){
                return rc;
            }
        }
        return null;
    }

}
