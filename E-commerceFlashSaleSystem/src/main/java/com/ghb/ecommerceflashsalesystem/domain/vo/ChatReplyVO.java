package com.ghb.ecommerceflashsalesystem.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 客服回复
 *
 * @param reply 模型生成的文本回复（当前为纯文本；后续如需商品卡片可扩展结构化字段）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatReplyVO {
    private String reply;
}
