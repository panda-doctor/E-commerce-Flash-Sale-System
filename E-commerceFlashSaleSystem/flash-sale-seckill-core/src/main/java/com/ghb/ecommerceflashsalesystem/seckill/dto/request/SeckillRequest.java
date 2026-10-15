package com.ghb.ecommerceflashsalesystem.seckill.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
/**
 * 秒杀请求
 *
 * 【字段取舍说明】
 * activityId 必填。userId 为旧客户端兼容字段，可省略；如传入须匹配服务端会话身份。
 * productId 保留为可选（非必填、不参与校验），理由：
 *   1. 对齐接口文档 4.8 请求示例，前端按文档传参不会因未知字段报错；
 *   2. 商品信息与秒杀价可由 activityId 从活动中推导，服务端 execute 并不依赖 productId，
 *      保留它仅作契约兼容与冗余透传，后续链路若需要可直接取用。
 */
@Data
public class SeckillRequest {
    /**
     * 秒杀活动编号
     */
    @NotNull(message = "活动ID不能为空")
    private Long activityId;

    /**
     * 用户编号
     */
    private Long userId;

    /**
     * 商品编号
     */
    private Long productId;
}
