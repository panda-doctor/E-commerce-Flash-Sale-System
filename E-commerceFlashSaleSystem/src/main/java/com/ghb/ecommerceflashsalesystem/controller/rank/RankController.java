package com.ghb.ecommerceflashsalesystem.controller.rank;

import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.constant.CacheKeyConstant;
import com.ghb.ecommerceflashsalesystem.domain.vo.RankEntryVO;
import com.ghb.ecommerceflashsalesystem.service.rank.SeckillRankService;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rank")
public class RankController {
    private final SeckillRankService seckillRankService;

    @GetMapping("/top10")
    public Result<List<RankEntryVO>> top10(@RequestParam @NotNull(message = "活动ID不能为空") Long activityId,
                                           @RequestParam(required = false, defaultValue = "10") Integer top) {

        // 限制 top 范围
        if(top == null || top < 1) {
            top = CacheKeyConstant.RANK_DEFAULT_TOP;
        }else if (top > CacheKeyConstant.RANK_MAX_TOP) {
            top = CacheKeyConstant.RANK_MAX_TOP;
        }
        List<RankEntryVO> list = seckillRankService.topN(activityId, top);
        return Result.success(list);
    }
}
