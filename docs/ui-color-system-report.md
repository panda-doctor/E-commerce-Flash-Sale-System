# 配色体系重构交付报告

> 范围：`frontend/`（React 18 + TS + Vite + Tailwind 3.4）。
> 只改视觉令牌、语义色使用与相关样式；未改 API 契约、未改业务判定、未新增依赖、未伪造数据。

## 一、Skill 使用

| 实际读取 | 落到哪里 |
|---|---|
| `.codebuddy/skills/improve-ui/SKILL.md` | 取证式审查：先扫描证据（色值统计、语义混用、对比度计算）再改；复用既有令牌与组件所有者；不新增第二套主题 |
| `.codebuddy/skills/baseline-ui/SKILL.md` | 约束执行：统一 `focus-ring`、`tabular-nums`、`prefers-reduced-motion`、hover 只动颜色/阴影、禁用态保留可辨识度、避免多强调色并列 |
| `.codebuddy/skills/ui-skills-root/SKILL.md` | 选择最小 Skill 组合（improve-ui + baseline-ui + 一份电商 DESIGN.md），未使用与本任务无关的 component-canvas |
| `.codebuddy/skills/design-md/mydealz/DESIGN.md` | 电商优惠流的层级方法：中性结构 + 单一促销强调色 + 价格优先 |
| `.codebuddy/skills/design-md/shopify/DESIGN.md` | 仅检索配色/排版条目做交叉校验（未逐节精读） |

## 二、审查发现（改前的真实问题）

| # | 问题 | 证据 |
|---|---|---|
| 1 | 错误提示语义串色 | `LoginPage` / `RegisterPage` / `SeckillPanel` / `ErrorState` 用 `bg-brand-soft`（促销浅红）+ `text-state-danger`，同一块同时表达"促销"与"失败" |
| 2 | 蓝色成为第二个装饰强调色 | `state-info` 被用于页脚图标、商品详情保障图标、活动提示、管理端图标等 9 处装饰；`QUEUING` 也用蓝色徽标 |
| 3 | 秒杀红外溢到导航与控件 | 顶部导航选中态、分页当前页、活动/订单筛选胶囊均为纯红实心，红色与价格抢注意力 |
| 4 | 大面积深色块 | 登录/注册左栏 `bg-ink-900` 整列深色，与"明亮简洁"目标冲突 |
| 5 | 彩色图标三色混排 | 首屏能力说明同时出现绿/红/蓝三个图标 |
| 6 | 弱化文字承载关键信息 | 原价、下单时间、活动时间、库存等使用 `ink-400`（对比度 2.58:1），价格对照难以阅读 |
| 7 | 硬编码色值 | `Badge` 内 `text-[#B26A00]`；另有 26 处 `bg-white` 绕过表面令牌 |
| 8 | 促销元素堆叠过多 | 每张秒杀卡同时有红徽标 + 红角标 + 红价格 + 红按钮 + 红 hover 边框 |
| 9 | 进度条过重 | 库存进度条 10px 纯红，列表中被误读为主视觉 |
| 10 | 状态与价格无差别 | 订单、活动、榜单名次等使用同一红色系，语义层级不清 |

## 三、最终配色令牌（唯一色源）

`frontend/tailwind.config.js` 定义，`src/styles/index.css` 通过 `theme()` 派生 CSS 变量（`--color-page-bg` 等），组件不写裸色值。

| 用途 | 令牌 | 色值 | 相对建议值的调整 |
|---|---|---|---|
| 页面底 | `page.DEFAULT` | `#F6F7F9` | 一致 |
| 白卡表面 | `page.card` | `#FFFFFF` | 一致 |
| 次级表面 | `page.subtle` | `#F0F2F5` | 一致 |
| 主文字 | `ink.900` | `#202124` | 一致 |
| 次级文字 | `ink.600` | `#5D6675` | 由 `#667085` 微调，保证在次级面上也 ≥5:1 |
| 弱化文字 | `ink.400` | `#98A2B3` | 一致，但**仅限**占位符/图标/禁用态 |
| 边框 | `state.line` | `#E5E7EB` | 一致 |
| 强边框 | `state.line-strong` | `#D0D5DD` | 一致 |
| 品牌/秒杀红 | `brand.DEFAULT` | `#D93A40` | 由 `#E5484D` 下压一档：原值白字仅 3.91:1，不达 AA |
| 红 hover | `brand.dark` | `#C22F36` | 由 `#D83A40` 加深 |
| 浅红底文字 | `brand.ink` | `#B3252B` | 新增：浅红底小字号专用 |
| 浅红底 | `brand.soft` | `#FFF1F0` | 一致 |
| 成功 | `state.success` | `#12724E` | 由 `#16865C` 加深（浅底文字需 ≥4.5） |
| 警告 | `state.warning` | `#9A5B08` | 由 `#B86E08` 加深（原值 3.98:1） |
| 错误 | `state.danger` | `#C42A1F` | 由 `#D92D20` 加深（浅底 4.86:1） |
| 信息 | `state.info` | `#475467` | 收敛为中性深灰，取消蓝色强调 |
| 阴影 | `shadow.card / card-hover / float` | `rgba(16,24,40,.04/.08/.12)` | 仅三档，不用彩色阴影 |

## 四、对比度实测（`scripts/contrast-check.mjs`，19 组全 PASS）

| 组合 | 实测 | 阈值 |
|---|---|---|
| 主文字 / 页面底 | 15.02:1 | 4.5 |
| 主文字 / 白卡 | 16.10:1 | 4.5 |
| 次级文字 / 白卡 | 5.80:1 | 4.5 |
| 次级文字 / 页面底 | 5.41:1 | 4.5 |
| 次级文字 / 次级面 | 5.17:1 | 4.5 |
| 白字 / 秒杀红按钮 | 4.54:1 | 4.5 |
| 白字 / 红按钮 hover | 5.59:1 | 4.5 |
| 秒杀红文字 / 白卡 | 4.54:1 | 4.5 |
| 浅红底小字 / 浅红底 | 5.94:1 | 4.5 |
| 成功 / 白卡 · 成功浅底 | 5.93 / 5.14 | 4.5 |
| 警告 / 白卡 · 警告浅底 | 5.42 / 4.74 | 4.5 |
| 危险 / 白卡 · 危险浅底 | 5.68 / 4.86 | 4.5 |
| 弱化文字 / 白卡 | 2.58:1 | 仅占位/图标，不设正文阈值 |

## 五、落地方式

### 5.1 公共组件

- `Badge`：浅底 + 深字 + 同色描边；`brand` 只给促销徽标，`success/warning/danger` 只给真实状态，`info` 中性深灰；删除硬编码 `#B26A00`。
- `Button`：primary 秒杀红 / secondary 白底发丝边 / ghost / danger 四档；统一 hover、active、`disabled:opacity-45`、`aria-busy` 与焦点环，颜色过渡仅 150ms。
- `Input`：边框 `state.line` → hover `line-strong` → focus 品牌色 + 2px 柔光环；错误态用危险色环。
- `Pagination`：当前页深色实心（`ink-900`），其余白底中性；分页不再染红。
- `ErrorState` / `ErrorBoundary`：危险色浅底 + 危险图标 + 请求号；不再借用促销浅红。
- `EmptyState`：中性图标底衬，红色不外溢。
- `Toast`：白面 + 语义细边框，info 中性；最多 3 条。
- 新增语义类：`.alert-danger`、`.notice`、`.filter-pill` / `.filter-pill-active`、`.card-hover`（中性边框 + 轻阴影），避免同类样式在多页复制。

### 5.2 页面

| 页面 | 改动 |
|---|---|
| 首页 | 导航选中态改中性；首屏三图标统一灰色；促销红只保留在"限时秒杀"标签、活动卡价格与主按钮；活动卡浅红底作为唯一促销面 |
| 商品卡片 | 白面 + 发丝边；促销标签浅红底深红字；原价 `ink-600` 删除线且**仅当活动价更低时展示**；商品名 hover 不再变红；非活动 CTA 改中性 hover；折扣角标用浅红 |
| 商品详情 | 保障图标中性；活动卡阶段徽章走统一 `phaseOfItem`；主图限宽 460px；"去秒杀"为唯一红色主操作 |
| 登录/注册 | 左栏由深色改为浅色中性面 + 白表单；错误提示统一 `.alert-danger`；链接与主按钮同一红色系 |
| 我的订单 | 筛选胶囊深色选中 + 真实计数；下单时间提升为 `ink-600`；轮询停止提示改中性 `.notice` |
| 活动列表 | 同款筛选胶囊；阶段徽章语义化（进行中=成功色，未开始=警告色，已结束=中性）；库存条收细至 6px |
| 榜单/个人中心 | 名次底色改中性深浅；头像底衬去红 |
| 管理端 | 图标去红/去蓝；活动快速选择改深色选中；上传按钮 hover 中性、焦点保留品牌色 |

### 5.3 响应式细节

筛选胶囊 `shrink-0 + whitespace-nowrap`，容器在 `<640px` 横向滚动、桌面自动换行；刷新按钮 `self-start` 避免被 flex 拉伸；移动端筛选不再折成两行。

## 六、验证结果（实际执行）

```
node scripts/contrast-check.mjs          19 组，0 未达标
npx tsc --noEmit -p tsconfig.app.json    exit 0（0 error）
npm run lint                             exit 0（0 error，6 warnings：既有 react-refresh）
npx vitest run                           59 passed / 5 skipped（13 passed | 1 skipped files）
npm run build                            ✓ built in 5.38s
node scripts/ui-check.mjs                16 个页面，0 横向溢出、0 页面异常
```

浏览器核查覆盖 4 视口（1440 / 1280 / 834 / 390）× 首页、活动列表、登录、商品详情（普通/有活动）、订单（桌面/移动）。

截图复查后又修掉的问题：

1. 移动端筛选胶囊折成两行 → 单行横向滚动；
2. 移动端刷新按钮被拉伸为整行 → `self-start`；
3. 活动价高于原价时仍展示划线原价 → 改为仅在真实优惠时展示；
4. 对比度不足的次级信息（原价、时间、库存、提示）统一提升到 `ink-600`；
5. 溢出检查器把横向滚动容器内的按钮误判为溢出 → 增加滚动祖先判定（检查脚本修正，非页面问题）。

## 七、遗留问题

| 项 | 原因 | 需要什么 |
|---|---|---|
| 4 张种子商品图 404 | `/uploads/202609/*` 文件未落盘（磁盘只有 `202610`） | 后端补文件或修种子数据；前端已降级为「暂无图片」 |
| 部分秒杀活动价格高于商品原价 | 回归测试创建的活动数据（如 29.99 对 20.49） | 后端/测试数据修正；前端已隐藏矛盾的划线价 |
| 未做暗色主题 | 不在本次范围 | 产品决策 |
| 未做像素级视觉回归 | 无基线截图工具 | 可接入 Playwright snapshot |

> `scripts/ui-check.mjs`、`scripts/contrast-check.mjs` 为本地核查脚本；前者依赖 `playwright-core`（未写入 `package.json`，`npm i --no-save playwright-core` 后可复跑），后者零依赖。
