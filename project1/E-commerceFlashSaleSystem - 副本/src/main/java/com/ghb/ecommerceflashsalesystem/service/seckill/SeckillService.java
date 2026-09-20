package com.ghb.ecommerceflashsalesystem.service.seckill;

import com.ghb.ecommerceflashsalesystem.domain.dto.request.SeckillRequest;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.SeckillResponse;

/**
 * 秒杀服务接口
 *
 * 【命名复盘说明】
 * v1 曾命名为 SeckillServer 且定义成 abstract class，不符合项目约定，存在两个问题：
 * 1. 项目 Service 层统一是「接口 + Impl 实现」模式（如 ProductService/ProductServiceImpl、
 *    SeckillActivityService/SeckillActivityServiceImpl），秒杀服务应遵循同样约定，而非抽象类。
 * 2. v1 曾在 SeckillServer 类内同时定义两个同签名 execute（一个有方法体、一个 abstract），
 *    Java 不允许同一类内同签名方法重复定义，编译直接失败。
 * 故重命名为 SeckillService（interface），实现类为 service/impl/SeckillServiceImpl。
 */
public interface SeckillService {

    /**
     * 执行秒杀：活动校验（缓存优先）-> Lua 原子扣减库存 -> 返回「排队中」
     *
     * @param request 秒杀请求（activityId、userId 必填）
     * @return 秒杀响应（result = QUEUED）
     */
    SeckillResponse execute(SeckillRequest request);
}
