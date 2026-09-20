package com.ghb.ecommerceflashsalesystem.domain.enums;

import lombok.Getter;

@Getter
public enum OrderStatusEnum {
    QUEUING(0, "排队中"),
    CREATED(1, "已创建"),
    CREATE_FAILED(2, "创建失败"),
    CANCELLED(3, "已取消");

    private final int code;
    private final String description;

    OrderStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static OrderStatusEnum fromValue(int code) {
        for (OrderStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
