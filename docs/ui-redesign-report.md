# 前端 UI 优化交付报告

> 范围：`frontend/`（React 18 + TS + Vite + Tailwind 3.4）。
> 原则：只改视觉与信息结构，**不动 API 契约、不改业务判定、不引入新依赖、不伪造数据**。

## 一、Skill 使用情况

| 实际读取的文件 | 指导了什么 |
|---|---|
| `.codebuddy/skills/improve-ui/SKILL.md` | 取证式审查：先找"证据"再改；复用现有 token/组件所有者；不把临时实现当成设计决策；改动前后要能对照验证 |
| `.codebuddy/skills/baseline-ui/SKILL.md` | 硬约束落地：Tailwind 默认尺度、`cn`、统一 z-index 层级、`tabular-nums`、`text-balance/pretty`、`line-clamp`、图标按钮 `aria-label`、交互 ≤200ms 且只动 transform/opacity、尊重 `prefers-reduced-motion`、避免无意义渐变/发光 |
| `.codebuddy/skills/ui-skills-root/SKILL.md` | 按任务选择最小 Skill 组合（本任务 = `improve-ui` + `baseline-ui` + 一份电商 `DESIGN.md` 参考） |
| `.codebuddy/skills/design-md/mydealz/DESIGN.md` | 电商优惠流的视觉语言：冷灰画布 + 白卡 + 发丝边框、价格用品牌色强调、胶囊按钮、信息密度与层级 |
| `.codebuddy/skills/design-md/shopify/DESIGN.md` | 仅检索配色/排版条目作为交叉校验（未逐节精读，故不作为主要依据） |

未使用：`component-canvas`（本项目要做的是产品内改版，不是组件画廊）、`fixing-accessibility`（本轮未做专项审计，仅在 token 层修了对比度）。

## 二、视觉体系

统一到 `tailwind.config.js` 的令牌，页面与组件不再各自写色值：

| 令牌 | 值 | 用途 |
|---|---|---|
| `brand` | `#E1251B` / dark `#B81B12` / soft `#FFF1EF` | 秒杀价、主按钮、活动区底色 |
| `page` | `#F2F4F6`（card `#FFFFFF`，subtle `#F8F9FB`） | 画布 / 卡片 / 次级底 |
| `ink` | 900 `#15181D` · 600 `#5B6470` · 400 `#717885` | 正文 / 次要 / 辅助（400 由 `#8A919C` 提升到 ≈4.8:1 对比度） |
| `state` | success `#0F9D6E` · warning `#F08C00` · danger `#D93A44` · info `#2F6BFF` · line `#E7E9ED` | 语义状态与分隔线 |
| `shadow` | `card` / `card-hover` / `float` | 静止卡片、悬停抬升、浮层 |
| 字号 | `heading 28/36` · `subheading 16/24` · `body 14/22` | 三级标题体系 |

基础层（`src/styles/index.css`）：`.container-page`（1200 内容宽 + 响应式边距）、`.card`、`.card-hover`（仅 transform/边框/阴影，200ms）、`.focus-ring`、`.chip`，以及全局 `prefers-reduced-motion` 降级。

## 三、页面与组件改动

| 位置 | 改动 |
|---|---|
| 首页 `ProductListPage` | 英雄区改为「左文案 + 右活动卡」双栏： eyebrow 胶囊、两行主标题、三条真实能力说明；活动卡展示进行中活动名称、秒杀价、剩余时间与「立即抢购」。搜索改为胶囊输入（保留 `aria-label` 与清空按钮），计数与刷新在移动端分列两端；商品网格 2/3/4 列自适应；空态/错误态/页码越界态保留 |
| 商品卡 `ProductCard` | 主图 1:1、白底发丝边框；最多两个角标（限时秒杀 / 已售罄）+ 左下折扣角标（仅在原价与秒杀价都真实存在且更低时计算）；价格层级 = 秒杀价大号品牌色 + 原价删除线；库存行等宽数字；CTA 按状态区分（去抢购 / 查看详情 / 已售罄）；悬停抬升与焦点环 |
| 活动卡 `ActivityCard` | 阶段徽章走统一 `phaseOfItem`（时间窗推导，不用快照 status）、价格、库存进度、倒计时胶囊、进入活动按钮，与首页/详情判定一致 |
| 商品详情 `ProductDetailPage` | 主图限宽 460px + 粘性展示，价格独立面板，活动卡展示阶段徽章/时间窗/「去秒杀」；缺失商品改为可恢复空态（不再返回 `null` 白屏）；加载骨架与错误态 |
| 秒杀活动列表 `ActivityListPage` | 标题层级 + 筛选计数（真实统计），刷新按钮胶囊化 |
| 我的订单 `OrdersPage` | 头部层级、筛选 tab 带真实数量、订单卡信息分组与悬停反馈、状态徽章沿用后端枚举映射 |
| 登录/注册 `AuthLayout` | 双栏外壳：左侧深色卖点面板、右侧表单；标题/副标题层级与移动端单列 |
| 公共 `Footer` | 三层结构：品牌说明 + 快捷入口 + 购物保障 + 版权条 |
| 公共 `Input` / `Pagination` / `Skeleton` / `EmptyState` | hover/focus/disabled 状态、胶囊分页、与卡片新比例对齐的骨架、图标底衬空态 |

## 四、验证结果（实际执行）

### 4.1 静态门禁

```
npx tsc --noEmit -p tsconfig.app.json   exit 0（0 error）
npm run lint                            exit 0（0 error，6 warnings：既有 react-refresh）
npx vitest run                          59 passed / 5 skipped（14 files）
npm run build                           ✓ built in 5.85s
```

> 说明：改版导致首页英雄区空态文案变化，已按原句回写使既有断言继续有效；未删除或跳过任何用例。

### 4.2 真实浏览器（Chrome headless，`scripts/ui-check.mjs`）

视口：1440×900、1280×800、834×1112、390×844；页面：首页、活动列表、登录、商品详情（普通 + 有活动）、订单（桌面 + 移动）。

| 检查项 | 结果 |
|---|---|
| 横向溢出（`scrollWidth > innerWidth` 或越界元素） | 14 个页面全部 0 溢出 |
| 页面 JS 异常 | 0 |
| 关键内容存在（在售好物 / 秒杀活动 / 登录 / 返回商品列表 / 我的订单） | 全部命中 |
| 商品卡数量与网格 | 桌面 4 列、平板 2 列、移动 2 列（卡片 173px，无挤压） |
| 商品详情关联活动 | 「有秒杀活动」+「进行中」+「去秒杀」正常渲染 |
| 移动端标题断行 | 修复前被挤成一行，现为两行 |

截图存于 `frontend/ui-shots/`（已加入 `.gitignore`）。复查中发现并修复的问题：

1. 移动端英雄标题因 `<br className="hidden sm:block">` 被压成一行 → 改为两个 `block` span；
2. 移动端计数与刷新按钮左对齐挤在一起 → `justify-between`；
3. 首页英雄区用后端快照 `status` 判定活动，与活动列表的时间窗判定不一致（会出现"列表显示进行中、首页说没有活动"）→ 统一改用 `phaseOfItem`；
4. 商品详情主图 640px 过大导致图文失衡 → 限宽 460px；
5. 折扣角标深色块过重 → 白底红字，与卡片标签统一；
6. 辅助文字 `#8A919C` 对比度不足 4.5:1 → 调至 `#717885`。

## 五、遗留问题

| 项 | 原因 | 影响 |
|---|---|---|
| 商品图 4 张 404（`/uploads/202609/*`） | 种子数据指向的文件在磁盘上不存在（`uploads/` 只有 3 个 `202610` 文件） | 前端按设计降级为「暂无图片」，不裂版；需后端补文件或修种子数据 |
| 未做暗色主题 | 不在本次范围 | — |
| 未做组件画廊可视化 | `component-canvas` 与本任务无关 | — |
| 交互动效仅 hover/focus 过渡 | `baseline-ui` 要求动效服务于理解；未新增入场动画 | 若要「更有发布会感」的动效需单独评审性能 |
