package com.ghb.ecommerceflashsalesystem.domain.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage implements Serializable  {

    private Long activityId;
    private Long productId;
    private Long userId;
    private String orderNo;          // 业务订单号
    private Long seckillPrice;       // 成交价快照，单位分
    private Long requestTime;        // 请求时间戳（毫秒）
}
