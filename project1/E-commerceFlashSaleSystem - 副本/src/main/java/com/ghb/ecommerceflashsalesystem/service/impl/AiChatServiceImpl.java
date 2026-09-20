package com.ghb.ecommerceflashsalesystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ghb.ecommerceflashsalesystem.common.api.ResultCode;
import com.ghb.ecommerceflashsalesystem.common.exception.BusinessException;
import com.ghb.ecommerceflashsalesystem.domain.dto.request.AiChatRequest;
import com.ghb.ecommerceflashsalesystem.domain.entity.Product;
import com.ghb.ecommerceflashsalesystem.domain.entity.SeckillActivity;
import com.ghb.ecommerceflashsalesystem.domain.enums.ActivityStatusEnum;
import com.ghb.ecommerceflashsalesystem.domain.enums.ProductStatusEnum;
import com.ghb.ecommerceflashsalesystem.domain.vo.ChatReplyVO;
import com.ghb.ecommerceflashsalesystem.mapper.ProductMapper;
import com.ghb.ecommerceflashsalesystem.mapper.SeckillActivityMapper;
import com.ghb.ecommerceflashsalesystem.service.ai.AiChatService;
import com.ghb.ecommerceflashsalesystem.service.ai.LlmChatClient;
import com.ghb.ecommerceflashsalesystem.service.ai.OpenAiChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 客服实现。
 *
 * <p>设计取舍（教学项目语境）：
 * <ul>
 *   <li><b>无向量库/无 RAG</b>：秒杀商城的"知识"主要是商品与活动目录，量级很小（种子几十条），
 *       直接在 system prompt 注入全量实时目录即可，模型基于真实数据作答（如"有什么键盘在卖"），
 *       不引入 embedding 等重型设施；若将来目录量大再换检索。</li>
 *   <li><b>目录实时性</b>：每次对话实时查 DB 组装（商品在售状态、活动时间/价格/库存），
 *       避免静态写死在提示词里过期；查询失败降级为空目录，不影响对话可用性。</li>
 *   <li><b>无状态会话</b>：历史由前端随请求带回，服务端只清洗/截断，不落库（见
 *       {@link AiChatRequest} 注释）。</li>
 * </ul>
 */
@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    /** 从历史中最多采纳的条数 */
    private static final int MAX_HISTORY_TURNS = 10;
    /** 单条历史/提问文本护栏长度 */
    private static final int MAX_TURN_CHARS = 600;
    /** 目录里最多展示的商品/活动行数，控制 token 开销 */
    private static final int CATALOG_LIMIT = 30;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final LlmChatClient chatClient;
    private final ProductMapper productMapper;
    private final SeckillActivityMapper activityMapper;

    public AiChatServiceImpl(LlmChatClient chatClient,
                             ProductMapper productMapper,
                             SeckillActivityMapper activityMapper) {
        this.chatClient = chatClient;
        this.productMapper = productMapper;
        this.activityMapper = activityMapper;
    }

    @Override
    public ChatReplyVO chat(String message, List<AiChatRequest.ChatTurn> history) {
        if (message == null || message.trim().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "问题不能为空");
        }
        String question = message.trim();
        if (question.length() > 500) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "问题过长，最多 500 字");
        }

        List<OpenAiChatMessage> messages = new ArrayList<>();
        messages.add(new OpenAiChatMessage("system", buildSystemPrompt()));

        // 历史清洗：角色归一化 + 单条截断后，仅取"最近"最多 MAX_HISTORY_TURNS 条（靠近当前问题）
        List<OpenAiChatMessage> normalized = new ArrayList<>();
        if (history != null) {
            for (AiChatRequest.ChatTurn turn : history) {
                String role = normalizeRole(turn == null ? null : turn.getRole());
                if (role == null) {
                    continue;
                }
                String content = turn.getContent() == null ? "" : turn.getContent().trim();
                if (content.isEmpty()) {
                    continue;
                }
                if (content.length() > MAX_TURN_CHARS) {
                    content = content.substring(0, MAX_TURN_CHARS) + "…";
                }
                normalized.add(new OpenAiChatMessage(role, content));
            }
        }
        if (normalized.size() > MAX_HISTORY_TURNS) {
            normalized = normalized.subList(normalized.size() - MAX_HISTORY_TURNS, normalized.size());
        }
        messages.addAll(normalized);
        messages.add(new OpenAiChatMessage("user", question));

        String reply = chatClient.chatCompletion(messages);
        return new ChatReplyVO(reply == null ? "" : reply.trim());
    }

    /** 前端可能传 user/assistant/ai/model，统一归一化为 OpenAI 角色；未知角色丢弃 */
    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        String r = role.trim().toLowerCase();
        if ("user".equals(r)) {
            return "user";
        }
        if ("assistant".equals(r) || "ai".equals(r) || "model".equals(r) || "bot".equals(r)) {
            return "assistant";
        }
        return null;
    }

    private String buildSystemPrompt() {
        String today = java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        return """
                【当前日期：%s】（判断秒杀场次状态以此为准：开始时间晚于今天才算"即将开始"）
                你是「小闪」，一个部署在闪电秒杀商城（E-commerce Flash Sale）的 AI 购物助手。
                """.formatted(today) +
                """
                服务风格：中文、简洁、友好、用词像电商客服；价格以「元」为单位展示。

                你了解本商城的秒杀规则：
                - 活动到点开抢、先到先得、每场每用户最多 1 单（重复抢会提示"请勿重复秒杀"）；
                - 抢单成功只是"排队成功"，订单异步落库，需到订单页查询最终结果；
                - 若提示"请求过于频繁"表示触发限流，稍等片刻再试；"已售罄"则本场结束。

                请结合下面实时目录回答（这是当前唯一可信的商品/活动事实来源）：
                - 用户询问某类商品（如"键盘/耳机"）时，从上架商品中筛选并列出名称、价格、库存；
                - 用户询问秒杀场次时，从活动列表中说明时间与状态，若此刻没有进行中的就如实告知；
                - 目录中没有的商品或场次，明确说"暂未找到"，不要编造名称与价格。

                """ + buildCatalog();
    }

    /**
     * 组装实时目录：在售商品 + 未结束的秒杀活动。任何查询失败都返回空串，
     * 仅记录日志，保证对话主链路不受目录影响。
     */
    private String buildCatalog() {
        StringBuilder sb = new StringBuilder();
        try {
            Map<Long, String> nameById = new HashMap<>();
            List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                    .eq(Product::getStatus, ProductStatusEnum.ON_SHELF.getCode())
                    .last("LIMIT " + CATALOG_LIMIT));
            sb.append("\n【当前在售商品】\n");
            for (Product p : products) {
                nameById.put(p.getId(), p.getName());
                sb.append("- #").append(p.getId()).append(' ')
                        .append(p.getName())
                        .append("，售价 ").append(yuan(p.getOriginalPrice()))
                        .append("，库存 ").append(p.getAvailableStock() == null ? "?" : p.getAvailableStock())
                        .append('\n');
            }
            if (products.isEmpty()) {
                sb.append("（暂无上架商品）\n");
            }
        } catch (Exception e) {
            log.warn("AI 商品目录组装失败，降级为空目录", e);
        }

        try {
            // M7：DB 的 status 是创建时快照、不会自动翻转（ENDED/SOLD_OUT 需另行维护），
            // 目录查询除排除 CANCELLED 外，还必须按 endTime 过滤掉已过期场次，
            // 否则历史活动会一直混进目录，误导模型以为"现在还有场次"。
            LocalDateTime now = LocalDateTime.now();
            List<SeckillActivity> activities = activityMapper.selectList(
                    new LambdaQueryWrapper<SeckillActivity>()
                            .in(SeckillActivity::getStatus,
                                    ActivityStatusEnum.NOT_STARTED.getCode(),
                                    ActivityStatusEnum.RUNNING.getCode())
                            .ge(SeckillActivity::getEndTime, now)
                            .orderByAsc(SeckillActivity::getStartTime)
                            .last("LIMIT " + CATALOG_LIMIT));
            sb.append("\n【秒杀活动】\n");
            for (SeckillActivity a : activities) {
                // 状态文案按实时时间窗推导（DB status 快照不可信），与 execute/check 口径一致
                sb.append("- #").append(a.getId()).append(' ')
                        .append(a.getActivityName())
                        .append("，秒杀价 ").append(yuan(a.getSeckillPrice()))
                        .append("，场次 ").append(fmt(a.getStartTime())).append(" ~ ").append(fmt(a.getEndTime()))
                        .append("，状态 ").append(deriveStatusText(a.getStartTime(), a.getEndTime()))
                        .append('\n');
            }
            if (activities.isEmpty()) {
                sb.append("（当前没有进行中或未开始的秒杀活动）\n");
            }
        } catch (Exception e) {
            log.warn("AI 活动目录组装失败，降级为空目录", e);
        }
        return sb.toString();
    }

    /** 分 -> 元文本 */
    private String yuan(Long fen) {
        return fen == null ? "未知" : String.format("¥%.2f", fen / 100.0);
    }

    private String fmt(java.time.LocalDateTime time) {
        return time == null ? "?" : time.format(DTF);
    }

    /** M7：秒杀场次状态按实时时间窗推导（不信任 DB status 快照），供目录文案使用 */
    private String deriveStatusText(LocalDateTime start, LocalDateTime end) {
        if (end == null) {
            return "未知";
        }
        LocalDateTime now = LocalDateTime.now();
        if (start != null && now.isBefore(start)) {
            return "未开始（即将开抢）";
        }
        if (!now.isBefore(end)) {
            return "已结束";
        }
        return "进行中";
    }
}
