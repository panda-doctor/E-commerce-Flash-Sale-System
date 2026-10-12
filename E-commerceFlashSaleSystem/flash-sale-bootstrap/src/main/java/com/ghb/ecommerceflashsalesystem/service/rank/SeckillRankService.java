package com.ghb.ecommerceflashsalesystem.service.rank;

import com.ghb.ecommerceflashsalesystem.domain.vo.RankEntryVO;

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
     */
    void recordSuccess(Long activityId, Long userId, Long successTime);

    /**
     * 获取活动榜单 TOP N
     *
     * @param activity
     * @param top      返回条数（自动限制在 1~100）
     * @return 榜单条目列表（按秒杀时间升序）
     */
    List<RankEntryVO> topN(Long activity, int top);

}
