package com.ghb.ecommerceflashsalesystem.domain.enums;

import lombok.Getter;

@Getter
public enum PreheatStatusEnum {
    NOT_PREHEATED(0, "未预热"),
    PREHEATED(1, "已预热"),
    PREHEAT_FAILED(2, "预热失败");

    private final int code;
    private final String description;

    PreheatStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static PreheatStatusEnum fromValue(int code) {
        for (PreheatStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
