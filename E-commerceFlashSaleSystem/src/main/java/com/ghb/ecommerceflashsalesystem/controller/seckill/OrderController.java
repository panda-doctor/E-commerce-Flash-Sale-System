package com.ghb.ecommerceflashsalesystem.controller.seckill;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seckill/orders")
/**
 * 点单控制
 */
public class OrderController {
    private final SeckillOrderMapper seckillOrderMapper;
    /**
     * 查询订单状态（interface 4.9）
     * GET /api/seckill/orders/{orderNo}
     */
    @GetMapping("/{orderNo}")
    public Result<Map<String, Object>> getOrderStatus(@PathVariable String orderNo) {
        SeckillOrder order = seckillOrderMapper.selectByOrderNo(orderNo);
        Map<String, Object> data = new HashMap<>();
        // 【复盘】曾误写 data.put("orderNo", order)——把整个订单实体塞进了 orderNo 键，应放请求参数本身
        data.put("orderNo", orderNo);
        if (order == null) {
            // 消息已入队但消费者尚未落库：返回排队中，前端轮询直到 CREATED
            data.put("status", "QUEUING");
        } else {
            String statusDesc = order.getStatus() == 1 ? "CREATED" : "QUEUING";
            data.put("status", statusDesc);
            data.put("activityId", order.getActivityId());
            data.put("productId", order.getProductId());
            data.put("userId", order.getUserId());
            data.put("seckillPrice", order.getSeckillPrice());
            data.put("orderId", order.getId());
            data.put("createTime", order.getCreatedAt());
        }
        return Result.success(data);
    }
}
