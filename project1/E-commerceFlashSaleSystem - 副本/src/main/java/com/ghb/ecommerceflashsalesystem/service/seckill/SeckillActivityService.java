package com.ghb.ecommerceflashsalesystem.service.seckill;

import com.ghb.ecommerceflashsalesystem.domain.dto.request.ActivityRequest;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.ActivityCheckResponse;
import com.ghb.ecommerceflashsalesystem.domain.vo.ActivityItemVO;
import com.ghb.ecommerceflashsalesystem.domain.vo.SeckillActivityVO;

import java.util.List;

/**
 * 秒杀活动服务接口
 */
public interface SeckillActivityService {

    /**
     * 活动广场列表（按开始时间倒序，最多 50 条），附商品信息与实时库存
     *
     * @return 活动列表条目
     */
    List<ActivityItemVO> listActivities();

    /**
     * 创建或更新活动
     *
     * @param request 活动请求DTO
     * @return 活动ID
     */
    Long createActivity(ActivityRequest request);

    /**
     * 获取活动详情（含实时库存，缓存优先）
     *
     * @param activityId 活动ID
     * @return 活动视图对象，不存在返回null
     */
    SeckillActivityVO getActivityDetail(Long activityId);

    /**
     * 校验用户是否可以参与活动
     *
     * @param activityId 活动ID
     * @param userId     用户ID
     * @return 校验响应
     */
    ActivityCheckResponse checkActivity(Long activityId, Long userId);
}