package com.ghb.ecommerceflashsalesystem.domain.enums;

import lombok.Getter;

@Getter
public enum ActivityStatusEnum {
    NOT_STARTED(0, "未开始"),
    IN_PROGRESS(1, "进行中"),
    ENDED(2, "已结束"),
    SOLD_OUT(3, "已售罄"),
    CANCELLED(4, "已取消");

    private final int code;
    private final String description;

    ActivityStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ActivityStatusEnum fromValue(int code) {
        for (ActivityStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
