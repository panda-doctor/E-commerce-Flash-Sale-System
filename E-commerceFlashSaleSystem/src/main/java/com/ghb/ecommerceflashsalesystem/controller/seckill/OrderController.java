package com.ghb.ecommerceflashsalesystem.controller.seckill;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.config.ApiAccessInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillOrder;
import com.ghb.ecommerceflashsalesystem.domain.enums.OrderStatusEnum;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seckill")
/**
 * 点单控制
 */
public class OrderController {
    private final SeckillOrderMapper seckillOrderMapper;
    /**
     * 查询订单状态（interface 4.9）
     * GET /api/seckill/orders/{orderNo}
     */
    @GetMapping("/orders/{orderNo}")
    public Result<Map<String, Object>> getOrderStatus(@PathVariable String orderNo) {
        SeckillOrder order = seckillOrderMapper.selectByOrderNo(orderNo);
        Map<String, Object> data = new HashMap<>();
        // 【复盘】曾误写 data.put("orderNo", order)——把整个订单实体塞进了 orderNo 键，应放请求参数本身
        data.put("orderNo", orderNo);
        // C1：按枚举输出 status name，不把 FAILED/CANCELLED 折叠成 QUEUING
        if (order == null) {
            // 消息已入队但消费者尚未落库：返回排队中，前端轮询直到 CREATED/FAILED
            data.put("status", OrderStatusEnum.QUEUING.name());
            data.put("statusDesc", OrderStatusEnum.QUEUING.getDescription());
        } else {
            OrderStatusEnum status = OrderStatusEnum.fromValue(order.getStatus());
            if (status == null) {
                status = OrderStatusEnum.QUEUING;
            }
            data.put("status", status.name());
            data.put("statusDesc", status.getDescription());
            data.put("activityId", order.getActivityId());
            data.put("productId", order.getProductId());
            data.put("userId", order.getUserId());
            data.put("seckillPrice", order.getSeckillPrice());
            data.put("orderId", order.getId());
            // C2：键名与 interface 4.9 对齐为 createdAt
            data.put("createdAt", order.getCreatedAt());
        }
        return Result.success(data);
    }

    /**
     * 查询用户秒杀订单（interface 4.10）
     * GET /api/seckill/users/{userId}/orders?activityId=1
     */
    @GetMapping("/users/{userId}/orders")
    public Result<List<Map<String, Object>>> listUserOrders(@PathVariable Long userId,
                                                             @RequestParam(required = false) Long activityId,
                                                             HttpServletRequest httpRequest) {
        Long authenticatedUserId = (Long) httpRequest.getAttribute(ApiAccessInterceptor.AUTHENTICATED_USER_ID);
        if (authenticatedUserId == null || !authenticatedUserId.equals(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请求用户与访问令牌不匹配");
        }
        List<SeckillOrder> orders = seckillOrderMapper.selectByUserIdAndActivityId(userId, activityId);
        List<Map<String, Object>> data = new ArrayList<>();
        for (SeckillOrder order : orders) {
            Map<String, Object> item = new HashMap<>();
            item.put("orderNo", order.getOrderNo());
            item.put("activityId", order.getActivityId());
            item.put("productId", order.getProductId());
            OrderStatusEnum status = OrderStatusEnum.fromValue(order.getStatus());
            item.put("status", status == null ? OrderStatusEnum.QUEUING.name() : status.name());
            item.put("createdAt", order.getCreatedAt());
            data.add(item);
        }
        return Result.success(data);
    }
}
