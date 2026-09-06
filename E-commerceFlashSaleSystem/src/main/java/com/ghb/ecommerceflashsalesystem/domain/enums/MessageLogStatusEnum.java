package com.ghb.ecommerceflashsalesystem.domain.enums;

import lombok.Getter;

@Getter
public enum MessageLogStatusEnum {
    PENDING(0, "待消费"),
    SUCCESS(1, "消费成功"),
    FAILED(2, "消费失败"),
    DEAD(3, "已进入死信");

    private final int code;
    private final String desc;

    MessageLogStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public static MessageLogStatusEnum fromValue(Integer code) {
        if (code == null)
            return null;
        for (MessageLogStatusEnum status : MessageLogStatusEnum.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        return null;
    }
}
