package com.ghb.ecommerceflashsalesystem.controller.seckill;


import com.ghb.ecommerceflashsalesystem.common.api.Result;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.config.ApiAccessInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import com.ghb.ecommerceflashsalesystem.domain.dto.response.ActivityCheckResponse;
import com.ghb.ecommerceflashsalesystem.domain.vo.ActivityItemVO;
import com.ghb.ecommerceflashsalesystem.domain.vo.SeckillActivityVO;
import com.ghb.ecommerceflashsalesystem.service.seckill.SeckillActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 秒杀活动查询控制器（用户端）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seckill")
public class SeckillActivityController {
    private final SeckillActivityService seckillActivityService;
    /**
     * 活动广场列表（前端首页"商品/活动"网格数据源）
     * GET /api/seckill/activities
     */
    @GetMapping("/activities")
    public Result<List<ActivityItemVO>> listActivities() {
        return Result.success(seckillActivityService.listActivities());
    }

    /**
     * 查询活动详情（接口 4.6）
     * GET /api/seckill/activities/{activityId}
     *
     * @param activityId 活动ID
     * @return 活动详情
     */
    @GetMapping("/activities/{activityId}")
    public Result<SeckillActivityVO > getActivities(@PathVariable Long activityId) {
        SeckillActivityVO vo = seckillActivityService.getActivityDetail(activityId);
        if (vo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "活动不存在，activityId=" + activityId);
        }
        log.info("查询活动详情成功，activityId={}", activityId);
        return Result.success(vo);
    }

    /**
     * 校验用户可否参与活动（接口 4.7）
     * GET /api/seckill/activities/{activityId}/check?userId=xxx
     *
     * @param activityId 活动ID
     * @param userId     用户ID（必填）
     * @return 校验结果
     */
    @GetMapping("/activities/{activityId}/check")
    public Result<ActivityCheckResponse> checkActivitiesUser(@PathVariable Long activityId,
                                                             @RequestParam Long userId,
                                                             HttpServletRequest httpRequest) {
        Long authenticatedUserId = (Long) httpRequest.getAttribute(ApiAccessInterceptor.AUTHENTICATED_USER_ID);
        if (authenticatedUserId == null || !authenticatedUserId.equals(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请求用户与访问令牌不匹配");
        }
        // service 层会处理活动不存在并抛出 BusinessException(NOT_FOUND)

        ActivityCheckResponse checkResponse = seckillActivityService.checkActivity( activityId, userId);
        log.info("活动校验完成，activityId={}, userId={}, canJoin={}",
                activityId, userId, checkResponse.getCanJoin());
        return Result.success(checkResponse);
    }
}
/**
 * 1. @PathVariable（路径变量注解）
 * 作用：从 URL 路径模板中获取值。
 *
 * 数据来源：URL 路径中的占位符（{}部分）。
 *
 * 适用场景：通常用于标识资源的 唯一 ID（RESTful 风格），比如获取、删除、更新某个具体资源。
 *
 * 请求示例：假设请求 URL 为 GET /api/activities/100
 *
 * @PathVariable 会将 URL 中的 100 赋值给 activityId 变量。
 *
 * 重要特性：默认必须传值（如果路径中没有该占位符，会报 404 错误）。如果方法参数名与路径占位符名称不一致，需指定 @PathVariable("id")。
 */
/**
 * 2. @RequestParam（请求参数注解）
 * 作用：从 HTTP 请求的查询字符串（Query String） 或 表单数据 中获取值。
 *
 * 数据来源：URL 中 ? 后面的键值对（key=value），或者 application/x-www-form-urlencoded 格式的 POST 请求体。
 *
 * 适用场景：通常用于筛选、分页、排序等非核心业务标识的参数，或者当前操作人的上下文信息（如这里的 userId）。
 *
 * 请求示例：假设请求 URL 为 GET /api/activities/100?userId=50&page=1
 *
 * @RequestParam 会将 ? 后面的 userId=50 赋值给 userId 变量。
 *
 * 重要特性：
 *
 * 默认必须传值（required=true），如果不传会抛出 MissingServletRequestParameterException。
 *
 * 可以设置默认值，如 @RequestParam(defaultValue = "1") int page。
 *
 * 如果参数名与方法变量名不一致，需指定 @RequestParam("user_id")。
 */
