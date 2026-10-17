package com.ghb.ecommerceflashsalesystem.analytics.service;

import com.ghb.ecommerceflashsalesystem.analytics.vo.RankEntryVO;

import java.util.List;

/**
 * 记录用户秒杀成功（榜单埋点）
 */
public interface SeckillRankService {
    /**
     * 记录用户秒杀成功（榜单埋点）
     *
     * @param activityId  活动ID
     * @param userId      用户ID
     * @param successTime 抢单成功时刻（毫秒，越早排名越前）；为 null 时回退当前时间
     * @param orderNo     订单号（P5：随事件写入投影，榜单展示直接读投影，读侧不再回调 order 服务）
     */
    void recordSuccess(Long activityId, Long userId, Long successTime, String orderNo);

    /**
     * 获取活动榜单 TOP N
     *
     * @param activity
     * @param top      返回条数（自动限制在 1~100）
     * @return 榜单条目列表（按秒杀时间升序）
     */
    List<RankEntryVO> topN(Long activity, int top);

}
