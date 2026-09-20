<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fenToYuan, formatDuration, parseServerTime } from '../utils/format'

// 方案商品卡片：方形主图 + 秒杀角标 + 已抢进度条 + 红价/划线价 + 已抢件数 + 状态按钮
// 状态：live 抢购中 / soon 即将开抢（预约提醒） / soldout 已抢光（置灰回顾）
// featured=true 时渲染为横向精选大卡（首页正在秒杀首张突出展示）
const props = defineProps({
  item: { type: Object, required: true },
  now: { type: Number, required: true },
  featured: { type: Boolean, default: false },
})
const router = useRouter()

const startMs = computed(() => parseServerTime(props.item.startTime))
const endMs = computed(() => parseServerTime(props.item.endTime))
const stock = computed(() =>
  props.item.stock != null ? Number(props.item.stock) : null,
)
const total = computed(() =>
  props.item.totalStock != null ? Number(props.item.totalStock) : 0,
)
const remaining = computed(() =>
  stock.value != null ? stock.value : Math.max(0, total.value),
)
// M8：后端返回 preheated 标记；旧数据缺失该字段时按已预热兼容处理
const preheated = computed(() => props.item.preheated !== false)

const phase = computed(() => {
  const it = props.item
  if (it.status === 'CANCELLED') return 'soldout'
  const now = props.now
  if (remaining.value === 0) return 'soldout'
  if (now < startMs.value) return 'soon'
  if (now >= endMs.value) return 'soldout'
  if (stock.value == null || !preheated.value) return 'unpreheated' // 未预热不可抢
  return 'live'
})

const soldPct = computed(() => {
  if (!total.value) return 0
  const sold = Math.max(0, total.value - remaining.value)
  return Math.min(100, Math.round((sold / total.value) * 100))
})
const lowStock = computed(
  () => phase.value === 'live' && remaining.value > 0 && remaining.value < 10,
)

const countdown = computed(() => {
  const now = props.now
  if (phase.value === 'unpreheated') return '库存未预热，点击进入'
  if (phase.value === 'soon') return `距开抢 ${formatDuration(startMs.value - now)}`
  if (phase.value === 'live') return `距结束 ${formatDuration(endMs.value - now)}`
  return ''
})

const fallbackChar = computed(() => (props.item.activityName || props.item.productName || '秒').slice(0, 1))

// 价格整数/小数拆分（¥ 后大号整数 + 小号小数，规范一致）
const splitPrice = computed(() => {
  const yuan = fenToYuan(props.item.seckillPrice)
  const [int, dec] = yuan.split('.')
  return { int, dec }
})

/* ---- 预约提醒：localStorage 记忆，到点由列表页扫描 toast ---- */
const remindKey = `fs_remind_${props.item.activityId}`
// 用 ref 而非 computed：computed 无响应依赖会一直缓存初始值，点击后 UI 不更新
const reminded = ref(localStorage.getItem(remindKey) === '1')
function toggleRemind(e) {
  e.stopPropagation()
  reminded.value = !reminded.value
  if (reminded.value) {
    localStorage.setItem(remindKey, '1')
    localStorage.setItem(remindKey + '_start', String(startMs.value))
  } else {
    localStorage.removeItem(remindKey)
    localStorage.removeItem(remindKey + '_start')
  }
}

function open() {
  // 附带配置总库存，供详情页计算"已抢 %"进度
  router.push({ path: `/activity/${props.item.activityId}`, query: props.item.totalStock ? { total: props.item.totalStock } : {} })
}
</script>

<template>
  <article
    class="acard"
    :class="[phase, { featured }]"
    tabindex="0"
    role="button"
    @click="open"
    @keydown.enter="open"
  >
    <!-- 主图 -->
    <div class="thumb">
      <img v-if="item.productImage" :src="item.productImage" :alt="item.productName" loading="lazy" />
      <div v-else class="thumb-fallback">{{ fallbackChar }}</div>
      <div class="thumb-shine"></div>
      <span class="badge-seckill">⚡ 秒杀</span>
      <span v-if="phase === 'live'" class="live-pulse"><i></i>抢购中</span>
      <span v-if="lowStock" class="rush">仅剩 {{ remaining }} 件</span>
    </div>

    <div class="body-wrap">
      <!-- 已抢进度条 -->
      <div class="sold-bar">
        <i :style="{ width: soldPct + '%' }" :class="{ hot: soldPct > 90 }"></i>
      </div>

      <div class="body">
        <p class="prod-name">{{ item.productName || item.activityName }}</p>
        <p class="act-name">{{ item.activityName }}</p>

        <div class="price-row">
          <span class="yen">¥</span>
          <span class="yen-int hnum">{{ splitPrice.int }}</span>
          <span class="yen-dec hnum">.{{ splitPrice.dec }}</span>
          <span v-if="item.originalPrice" class="orig hnum">¥{{ fenToYuan(item.originalPrice) }}</span>
        </div>

        <div class="foot">
          <span class="sold-text">已抢 {{ Math.max(0, total - remaining) }} 件</span>
          <button class="act-btn" :class="phase" @click="phase === 'soon' ? toggleRemind($event) : open()">
            <template v-if="phase === 'live'">立即抢购</template>
            <template v-else-if="phase === 'soon'">{{ reminded ? '已预约' : '提醒我' }}</template>
            <template v-else-if="phase === 'unpreheated'">库存未预热</template>
            <template v-else>已抢光</template>
          </button>
        </div>

        <p v-if="countdown" class="cd hnum" :class="{ ur: lowStock }">{{ countdown }}</p>
      </div>
    </div>
  </article>
</template>

<style scoped>
.acard {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid var(--border);
  box-shadow: var(--shadow-1);
  transition: transform 0.22s var(--ease-out), box-shadow 0.22s var(--ease-out),
    border-color 0.22s var(--ease-out);
}
.acard.live:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-3), 0 0 0 1px rgba(255, 45, 85, 0.15);
  border-color: rgba(255, 45, 85, 0.25);
}
.acard:not(.live):hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-2);
}
.acard:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px var(--accent-ring);
}
.body-wrap {
  display: flex;
  flex-direction: column;
  flex: 1;
}

/* 主图 */
.thumb {
  position: relative;
  aspect-ratio: 1 / 1;
  background: linear-gradient(140deg, #fff0ef, #ffe3de);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s var(--ease-out);
}
.acard:hover .thumb img {
  transform: scale(1.06);
}
.acard.soldout .thumb {
  filter: grayscale(0.75);
  opacity: 0.7;
}
.thumb-fallback {
  font-size: 44px;
  font-weight: 900;
  color: #fff;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff5e3a, #ff2d55);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(255, 45, 85, 0.15);
}
.thumb-shine {
  position: absolute;
  inset: 0;
  background: linear-gradient(120deg, transparent 30%, rgba(255, 255, 255, 0.18) 50%, transparent 70%);
  pointer-events: none;
}
.badge-seckill {
  position: absolute;
  left: 0;
  top: 14px;
  background: linear-gradient(120deg, #ff2d55, #ff5e3a);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  padding: 4px 12px 4px 9px;
  border-radius: 0 999px 999px 0;
  box-shadow: 0 2px 8px rgba(255, 45, 85, 0.3);
}
.live-pulse {
  position: absolute;
  right: 10px;
  top: 12px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(4px);
  color: #ff2d55;
  font-size: 11px;
  font-weight: 800;
  padding: 3px 10px;
  border-radius: 999px;
  box-shadow: var(--shadow-1);
}
.live-pulse i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ff2d55;
  animation: blink 1.3s ease infinite;
}
@keyframes blink {
  50% { opacity: 0.25; }
}
.rush {
  position: absolute;
  right: 10px;
  bottom: 10px;
  color: #ff2d55;
  background: rgba(255, 255, 255, 0.97);
  font-size: 11px;
  font-weight: 800;
  padding: 3px 10px;
  border-radius: 999px;
  box-shadow: var(--shadow-1);
  animation: rushPulse 1.2s ease-in-out infinite;
}
@keyframes rushPulse {
  50% { transform: scale(1.06); background: #ffe3e0; }
}

/* 已抢进度条（高光头 + >90% 闪烁） */
.sold-bar {
  height: 4px;
  background: var(--surface-3);
}
.sold-bar i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #ff9500, #ff2d55);
  transition: width 0.45s var(--ease-out);
}
.sold-bar i.hot {
  animation: soldFlash 0.9s ease infinite;
}
@keyframes soldFlash {
  50% { opacity: 0.45; }
}

.body {
  padding: 12px 14px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}
.prod-name {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 39px;
}
.act-name {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 3px;
  margin-top: 2px;
}
.yen {
  color: #ff2d55;
  font-weight: 800;
  font-size: 15px;
}
.yen-int {
  color: #ff2d55;
  font-size: 28px;
  font-weight: 900;
  letter-spacing: -0.5px;
  font-family: var(--font-num);
}
.yen-dec {
  color: #ff2d55;
  font-size: 14px;
  font-weight: 700;
  font-family: var(--font-num);
}
.orig {
  margin-left: auto;
  font-size: 12px;
  color: var(--text-3);
  text-decoration: line-through;
  font-family: var(--font-num);
}

.foot {
  margin-top: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.sold-text {
  font-size: 12px;
  color: var(--text-3);
  font-weight: 500;
}
.act-btn {
  padding: 7px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 700;
  transition: all 0.16s var(--ease-out);
}
.act-btn.live {
  color: #fff;
  background: linear-gradient(120deg, #ff2d55, #ff5e3a);
  box-shadow: 0 4px 12px rgba(255, 45, 85, 0.35);
}
.act-btn.live:hover {
  filter: brightness(1.06);
  transform: translateY(-1px);
}
.act-btn.live:active {
  transform: scale(0.95);
}
.act-btn.soon {
  color: #ff9500;
  border: 1px solid rgba(255, 149, 0, 0.5);
  background: var(--warning-soft);
}
.act-btn.soon:hover {
  background: rgba(255, 149, 0, 0.2);
}
.act-btn.soldout {
  color: var(--text-3);
  background: var(--surface-3);
  cursor: default;
}
.act-btn.unpreheated {
  color: #b45309;
  border: 1px dashed rgba(180, 83, 9, 0.55);
  background: rgba(255, 237, 213, 0.7);
}
.acard.unpreheated .thumb {
  filter: saturate(0.75);
}

.cd {
  margin: 0;
  font-size: 12px;
  color: var(--text-3);
  font-family: var(--font-num);
}
.cd.ur {
  color: #ff2d55;
  font-weight: 700;
}

/* ============ 精选大卡（横向） ============ */
.acard.featured {
  flex-direction: row;
}
.acard.featured .thumb {
  width: 220px;
  flex: none;
  aspect-ratio: auto;
}
.acard.featured .body-wrap {
  flex: 1;
  min-width: 0;
}
.acard.featured .body {
  padding: 20px 22px;
  justify-content: center;
  gap: 6px;
}
.acard.featured .prod-name {
  font-size: 17px;
  -webkit-line-clamp: 1;
  min-height: 0;
}
.acard.featured .act-name {
  font-size: 13px;
}
.acard.featured .yen-int {
  font-size: 34px;
}
.acard.featured .yen {
  font-size: 17px;
}
.acard.featured .yen-dec {
  font-size: 16px;
}
.acard.featured .orig {
  font-size: 14px;
}
.acard.featured .foot {
  margin-top: 12px;
}
.acard.featured .sold-text {
  font-size: 13px;
}
.acard.featured .act-btn {
  padding: 9px 20px;
  font-size: 14px;
}
.acard.featured .cd {
  font-size: 13px;
}
.acard.featured .badge-seckill {
  font-size: 13px;
  padding: 5px 14px 5px 10px;
}

@media (max-width: 760px) {
  .acard.featured .thumb {
    width: 140px;
  }
  .acard.featured .body {
    padding: 14px 16px;
  }
  .acard.featured .yen-int {
    font-size: 26px;
  }
  .acard.featured .prod-name {
    font-size: 15px;
  }
}
</style>
