package com.ghb.ecommerceflashsalesystem.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 管理端人工回放死信时指定原 Stream 消息 ID。 */
@Data
public class DeadLetterReplayRequest {

    @NotBlank(message = "原消息编号不能为空")
    private String originalMessageId;
}
