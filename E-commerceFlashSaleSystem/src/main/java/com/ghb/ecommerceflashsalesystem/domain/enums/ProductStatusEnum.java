package com.ghb.ecommerceflashsalesystem.domain.enums;

import lombok.Getter;

@Getter
public enum ProductStatusEnum {
    OFF_SHELF(0, "下架"),
    ON_SHELF(1, "上架");

    private final int code;
    private final String description;

    ProductStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ProductStatusEnum fromValue(int code) {
        for (ProductStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
