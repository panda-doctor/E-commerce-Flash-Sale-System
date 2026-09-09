<script setup>
import { ref, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { fenToYuan, parseServerTime } from '../utils/format'

/* ============================================================
   AI 客服 · 对接真实后端大模型
   对话走 POST /api/support/chat（后端调 OpenAI 兼容大模型，见
   E-commerceFlashSaleSystem AiSupportController / AiChatServiceImpl）
   ============================================================ */

// AI 助手信息
const aiName = '小闪'
const aiAvatar = '🤖'
const WELCOME_TEXT =
  '你好！我是小闪，商场的 AI 购物助手 ⚡\n我可以帮你快速了解商品情况、查询秒杀场次、推荐高性价比好物。\n试试问我：「今天有什么秒杀？」或点击下方快捷问题吧～'

const router = useRouter()

// 对话消息列表（初始为欢迎语）
const messages = ref([{ role: 'ai', type: 'text', content: WELCOME_TEXT, time: '刚刚' }])
// 提交给后端的多轮历史（不含欢迎语与"正在输入"），随请求带回保持上下文
const historyMsgs = ref([])

// 实时秒杀活动列表（侧栏"热门活动"，来源 GET /api/seckill/activities）
const hotActivities = ref([])
let hotRefreshTimer = null // 侧栏热门活动静默刷新句柄（F2/F8：切后台暂停）

// 正在输入指示
const typing = ref(false)

// 快捷问题
const quickQuestions = [
  { icon: '🔥', text: '今天有什么秒杀？', q: '今天有什么秒杀活动？' },
  { icon: '⌨️', text: '帮我找键盘类商品', q: '帮我找键盘类的商品' },
  { icon: '💰', text: '推荐高性价比商品', q: '推荐几个高性价比的商品' },
  { icon: '📦', text: '哪些商品还在售？', q: '现在还有哪些商品在售？' },
  { icon: '🕐', text: '即将开抢的场次', q: '有哪些即将开抢的场次？' },
  { icon: '🎁', text: '有什么新品上架', q: '最近有什么新品上架？' },
]

// 活动状态 -> 侧栏标签（文案 + 颜色类）
const ACTIVITY_TAG = {
  RUNNING: { text: '秒杀中', cls: 'red' },
  NOT_STARTED: { text: '即将开抢', cls: 'orange' },
  ENDED: { text: '已结束', cls: 'blue' },
  SOLD_OUT: { text: '已售罄', cls: 'gray' },
  CANCELLED: { text: '已取消', cls: 'gray' },
}

/**
 * F2：侧栏状态按本地实时时间窗 + 实时库存推导，不再信任后端列表的 status 快照。
 * DB status 是创建/预热时的快照、不会自动翻转 RUNNING——预热后到点/已过期的活动若按
 * 快照判"即将开抢"会一直误标。口径与首页 ActivityListView.phaseOf 一致：
 * CANCELLED 显式排除 → now<startTime 预告 → now≥endTime 已结束 → stock=0 已售罄 → 秒杀中。
 */
function livePhaseOf(a, now) {
  if (a.status === 'CANCELLED') return 'CANCELLED'
  const start = parseServerTime(a.startTime)
  const end = parseServerTime(a.endTime)
  if (start > 0 && now < start) return 'NOT_STARTED'
  if (now >= end) return 'ENDED'
  const stock = a.stock != null ? Number(a.stock) : null
  if (stock === 0) return 'SOLD_OUT'
  return 'RUNNING'
}

// 侧栏实时热门活动：取"秒杀中 / 即将开抢"的最近几场（已结束/已售罄/已取消不推荐）
async function loadActivities() {
  try {
    const now = Date.now()
    const list = (await api.listActivities()) || []
    const hot = list
      .map((a) => ({ a, phase: livePhaseOf(a, now) }))
      .filter(({ phase }) => phase === 'RUNNING' || phase === 'NOT_STARTED')
      .sort((x, y) => {
        // 进行中优先；同为进行中/预告时按开始时间近者优先
        if (x.phase !== y.phase) return x.phase === 'RUNNING' ? -1 : 1
        return parseServerTime(x.a.startTime) - parseServerTime(y.a.startTime)
      })
      .slice(0, 5)
    hotActivities.value = hot.map(({ a, phase }) => {
      const tag = ACTIVITY_TAG[phase] || { text: phase, cls: 'blue' }
      return {
        activityId: a.activityId,
        name: a.productName || a.activityName || `活动 #${a.activityId}`,
        price: fenToYuan(a.seckillPrice),
        origin: a.originalPrice ? fenToYuan(a.originalPrice) : '',
        image: a.productImage || '',
        tag: tag.text,
        tagCls: tag.cls,
      }
    })
  } catch (e) {
    hotActivities.value = []
  }
}

function goActivity(id) {
  router.push(`/activity/${id}`)
}

// 清空对话 / 新建对话：重置消息与历史
function resetChat() {
  historyMsgs.value = []
  messages.value = [{ role: 'ai', type: 'text', content: WELCOME_TEXT, time: '刚刚' }]
  scrollToBottom()
}

// 输入与发送
const inputText = ref('')
const scrollRef = ref(null)

function scrollToBottom() {
  nextTick(() => {
    if (scrollRef.value) {
      scrollRef.value.scrollTop = scrollRef.value.scrollHeight
    }
  })
}

function pushMessage(msg) {
  messages.value.push({ ...msg, time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) })
  scrollToBottom()
}

async function sendText(text) {
  // F6：请求在途时忽略重复触发（回车连发 / 连点 / 快捷问题），防并发叠加空耗外部配额
  if (typing.value) return
  const q = (text ?? inputText.value).trim()
  if (!q) return
  inputText.value = ''
  pushMessage({ role: 'user', type: 'text', content: q })
  typing.value = true
  scrollToBottom()
  try {
    // 多轮上下文：取最近 10 条已确认对话随请求带回
    const history = historyMsgs.value.slice(-10)
    const data = await api.aiChat({ message: q, history })
    const reply = data?.reply?.trim() || '（未收到回复，请稍后再试）'
    pushMessage({ role: 'ai', type: 'text', content: reply })
    historyMsgs.value.push({ role: 'user', content: q })
    historyMsgs.value.push({ role: 'assistant', content: reply })
  } catch (e) {
    // 后端返回 50300（未配置密钥）/50301（调用失败）等，message 已含可读提示
    pushMessage({
      role: 'ai',
      type: 'text',
      content: `抱歉，${e?.message || 'AI 服务暂时不可用，请稍后重试'}`,
    })
  } finally {
    typing.value = false
  }
}

function askQuick(q) {
  sendText(q)
}

// 初始化：载入实时活动 + 定位到底部；热门活动 30s 静默刷新（后台标签页暂停）
onMounted(() => {
  scrollToBottom()
  loadActivities()
  hotRefreshTimer = setInterval(() => {
    if (!document.hidden) loadActivities()
  }, 30_000)
})

onBeforeUnmount(() => {
  clearInterval(hotRefreshTimer)
  hotRefreshTimer = null
})
</script>

<template>
  <div class="ai-service container">
    <!-- 深色科技 Hero -->
    <header class="ai-hero">
      <div class="hero-bg"></div>
      <div class="hero-grid"></div>
      <div class="hero-content">
        <div class="hero-left">
          <div class="hero-avatar">
            <span class="avatar-ring"></span>
            <span class="avatar-core">{{ aiAvatar }}</span>
          </div>
          <div class="hero-titles">
            <span class="hero-tag">✨ AI SHOPPING ASSISTANT</span>
            <h1 class="hero-title">AI 购物助手 · {{ aiName }}</h1>
            <p class="hero-desc">快速了解商场商品情况，智能推荐秒杀好物，7×24 在线为你服务</p>
          </div>
        </div>
        <div class="hero-stats">
          <div class="stat">
            <span class="stat-num">7×24</span>
            <span class="stat-label">在线服务</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat">
            <span class="stat-num">秒级</span>
            <span class="stat-label">响应速度</span>
          </div>
          <div class="stat-divider"></div>
          <div class="stat">
            <span class="stat-num">智能</span>
            <span class="stat-label">商品推荐</span>
          </div>
        </div>
      </div>
    </header>

    <!-- 主体双栏 -->
    <div class="ai-layout">
      <!-- 左侧侧边栏 -->
      <aside class="ai-sidebar">
        <!-- 新建对话 -->
        <button class="new-chat" @click="resetChat">
          <span class="nc-ico">✚</span>
          <span>新建对话</span>
        </button>

        <!-- 快捷问题 -->
        <div class="side-section">
          <div class="side-head">
            <span class="side-ico">💡</span>
            <h3>快捷问题</h3>
          </div>
          <div class="quick-list">
            <button v-for="q in quickQuestions" :key="q.text" class="quick-item" @click="askQuick(q.q)">
              <span class="qi-ico">{{ q.icon }}</span>
              <span class="qi-text">{{ q.text }}</span>
              <span class="qi-arrow">›</span>
            </button>
          </div>
        </div>

        <!-- 热门活动（实时：来自后端活动列表） -->
        <div class="side-section">
          <div class="side-head">
            <span class="side-ico">🔥</span>
            <h3>热门活动</h3>
          </div>
          <div v-if="hotActivities.length" class="hot-list">
            <div v-for="p in hotActivities" :key="p.activityId" class="hot-item" @click="goActivity(p.activityId)">
              <span class="hot-ico">
                <img v-if="p.image" :src="p.image" alt="" />
                <span v-else class="hot-emoji">🛍️</span>
              </span>
              <div class="hot-info">
                <span class="hot-name">{{ p.name }}</span>
                <span class="hot-price">{{ p.price }}</span>
              </div>
              <span class="hot-tag" :class="`hot-tag-${p.tagCls}`">{{ p.tag }}</span>
            </div>
          </div>
          <p v-else class="hot-empty">暂无可抢活动，去管理台创建并预热吧～</p>
        </div>

        <!-- 能力说明 -->
        <div class="side-section capability">
          <div class="cap-ico">⚡</div>
          <p class="cap-text">AI 助手可查询商品信息、秒杀场次、库存状态，并基于你的偏好智能推荐好物。</p>
        </div>
      </aside>

      <!-- 右侧对话主区 -->
      <section class="ai-chat">
        <!-- 对话头部条 -->
        <div class="chat-bar">
          <div class="chat-bar-left">
            <span class="cb-avatar">{{ aiAvatar }}</span>
            <div class="cb-info">
              <span class="cb-name">{{ aiName }} · AI 助手</span>
              <span class="cb-status">
                <i class="cb-dot"></i>在线 · 随时为你服务
              </span>
            </div>
          </div>
          <div class="chat-bar-right">
            <button class="cb-btn" title="清空对话" @click="resetChat">🗑</button>
            <button class="cb-btn" title="语音输入">🎤</button>
            <button class="cb-btn" title="更多">⋯</button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div ref="scrollRef" class="chat-body">
          <div
            v-for="(msg, i) in messages"
            :key="i"
            class="msg-row"
            :class="msg.role === 'user' ? 'msg-user' : 'msg-ai'"
          >
            <!-- AI 头像 -->
            <div v-if="msg.role === 'ai'" class="msg-avatar">{{ aiAvatar }}</div>

            <div class="msg-bubble-wrap">
              <!-- 文本气泡 -->
              <div v-if="msg.type === 'text'" class="msg-bubble" :class="msg.role === 'user' ? 'bubble-user' : 'bubble-ai'">
                <p class="msg-text">{{ msg.content }}</p>
              </div>

              <!-- 商品推荐卡片 -->
              <div v-else-if="msg.type === 'products'" class="msg-products">
                <div class="msg-bubble bubble-ai">
                  <p class="msg-text">{{ msg.content }}</p>
                </div>
                <div class="product-cards">
                  <div v-for="p in msg.products" :key="p.id" class="product-card">
                    <div class="pc-thumb">
                      <span class="pc-ico">📦</span>
                      <span class="pc-tag" :class="`pc-tag-${p.tagColor || 'red'}`">{{ p.tag }}</span>
                    </div>
                    <div class="pc-body">
                      <h4 class="pc-name">{{ p.name }}</h4>
                      <p class="pc-desc">{{ p.desc }}</p>
                      <div class="pc-price-row">
                        <span class="pc-price">{{ p.price }}</span>
                        <span class="pc-origin">{{ p.origin }}</span>
                      </div>
                    </div>
                    <button class="pc-btn">去抢购</button>
                  </div>
                </div>
              </div>

              <span class="msg-time">{{ msg.time }}</span>
            </div>
          </div>

          <!-- 正在输入指示 -->
          <div v-if="typing" class="msg-row msg-ai">
            <div class="msg-avatar">{{ aiAvatar }}</div>
            <div class="msg-bubble-wrap">
              <div class="msg-bubble bubble-ai typing-bubble">
                <span class="typing-dot"></span>
                <span class="typing-dot"></span>
                <span class="typing-dot"></span>
              </div>
              <span class="msg-time">{{ aiName }} 正在输入…</span>
            </div>
          </div>

          <!-- 引导提示 -->
          <div v-if="messages.length <= 1" class="chat-hint">
            <span class="hint-ico">👆</span>
            <span>点击左侧快捷问题，或在下方输入框开始对话</span>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="chat-input">
          <div class="input-bar">
            <button class="ib-btn" title="附件">📎</button>
            <button class="ib-btn" title="图片">🖼</button>
            <input
              v-model="inputText"
              type="text"
              class="ib-input"
              placeholder="输入你的问题，如「今天有什么秒杀？」"
              @keydown.enter="sendText()"
            />
            <button class="ib-send" :class="{ 'send-active': inputText.trim() }" :disabled="typing || !inputText.trim()" @click="sendText()">
              <span class="send-ico">➤</span>
              <span>发送</span>
            </button>
          </div>
          <p class="input-tip">回复由后端接入的大模型实时生成，回答基于商城在售商品与秒杀场次，仅供参考</p>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* AI 客服蓝紫色主题变量（局部覆盖，不影响全局秒杀红） */
.ai-service {
  --ai-primary: #6366f1;
  --ai-primary-hover: #5457e6;
  --ai-soft: rgba(99, 102, 241, 0.1);
  --ai-deep: #0f0b2e;
  --ai-deep-2: #1a1240;
  --ai-grad: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  --ai-grad-deep: linear-gradient(135deg, #0f0b2e 0%, #1a1240 100%);
  --ai-glow: radial-gradient(circle at 70% 30%, rgba(99, 102, 241, 0.4), transparent 55%);

  padding: 28px 28px 80px;
}

/* ---------- 深色科技 Hero ---------- */
.ai-hero {
  position: relative;
  border-radius: var(--radius-2xl);
  padding: 40px 40px 38px;
  margin-bottom: 24px;
  overflow: hidden;
  background: var(--ai-grad-deep);
  color: #fff;
  box-shadow: var(--shadow-2);
}
.hero-bg {
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 75% 25%, rgba(99, 102, 241, 0.4), transparent 55%),
    radial-gradient(circle at 20% 80%, rgba(139, 92, 246, 0.28), transparent 50%);
  pointer-events: none;
}
.hero-grid {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px);
  background-size: 36px 36px;
  mask-image: radial-gradient(ellipse at center, #000 30%, transparent 80%);
  pointer-events: none;
}
.hero-content {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  flex-wrap: wrap;
}
.hero-left {
  display: flex;
  align-items: center;
  gap: 22px;
}

/* AI 头像 */
.hero-avatar {
  position: relative;
  width: 68px;
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.avatar-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: conic-gradient(from 0deg, #6366f1, #8b5cf6, #ec4899, #6366f1);
  animation: spinRing 4s linear infinite;
  filter: blur(0.5px);
}
.avatar-core {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--ai-deep-2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  box-shadow: 0 0 0 3px var(--ai-deep-2), 0 8px 24px -6px rgba(99, 102, 241, 0.6);
}
@keyframes spinRing {
  to { transform: rotate(360deg); }
}

.hero-titles {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.hero-tag {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.55);
  margin-bottom: 6px;
}
.hero-title {
  margin: 0;
  font-size: 30px;
  font-weight: 900;
  letter-spacing: 0.5px;
  background: linear-gradient(120deg, #fff 0%, #c7d2fe 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.hero-desc {
  margin: 6px 0 0;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
}

/* Hero 统计 */
.hero-stats {
  display: flex;
  align-items: center;
  gap: 20px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.12);
  backdrop-filter: blur(8px);
  padding: 14px 24px;
  border-radius: 16px;
}
.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  line-height: 1.2;
}
.stat-num {
  font-size: 22px;
  font-weight: 900;
  font-family: var(--font-num);
  background: linear-gradient(120deg, #c7d2fe, #f0abfc);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.stat-label {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.6);
  margin-top: 2px;
}
.stat-divider {
  width: 1px;
  height: 28px;
  background: rgba(255, 255, 255, 0.15);
}

/* ---------- 双栏布局 ---------- */
.ai-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 22px;
  align-items: stretch;
}

/* ---------- 左侧侧边栏 ---------- */
.ai-sidebar {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.new-chat {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 46px;
  border-radius: var(--radius-m);
  background: var(--ai-grad);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.5px;
  box-shadow: 0 6px 18px -6px rgba(99, 102, 241, 0.55);
  transition: transform 0.18s var(--ease-out), filter 0.18s;
}
.new-chat:hover {
  transform: translateY(-1px);
  filter: brightness(1.08);
}
.new-chat:active {
  transform: scale(0.98);
}
.nc-ico {
  font-size: 18px;
}

.side-section {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-l);
  padding: 16px;
  box-shadow: var(--shadow-1);
}
.side-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.side-ico {
  font-size: 17px;
}
.side-head h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 800;
  color: var(--text);
}

/* 快捷问题列表 */
.quick-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.quick-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: transparent;
  border: 1px solid transparent;
  text-align: left;
  width: 100%;
  transition: all 0.16s var(--ease-out);
}
.quick-item:hover {
  background: var(--ai-soft);
  border-color: rgba(99, 102, 241, 0.2);
  transform: translateX(3px);
}
.qi-ico {
  font-size: 16px;
  flex-shrink: 0;
}
.qi-text {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-2);
}
.quick-item:hover .qi-text {
  color: var(--ai-primary);
}
.qi-arrow {
  font-size: 16px;
  color: var(--text-3);
  opacity: 0;
  transition: opacity 0.16s;
}
.quick-item:hover .qi-arrow {
  opacity: 1;
  color: var(--ai-primary);
}

/* 热门商品列表 */
.hot-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.hot-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 10px;
  transition: background 0.16s;
  cursor: pointer;
}
.hot-item:hover {
  background: var(--surface-2);
}
.hot-ico {
  font-size: 22px;
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-2);
  border-radius: 10px;
  flex-shrink: 0;
}
.hot-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  line-height: 1.3;
  min-width: 0;
}
.hot-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.hot-price {
  font-size: 13px;
  font-weight: 800;
  color: var(--accent);
  font-family: var(--font-num);
}
.hot-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: 999px;
  white-space: nowrap;
  flex-shrink: 0;
}
.hot-tag-red { color: #fff; background: var(--grad-cta); }
.hot-tag-orange { color: var(--aux); background: var(--aux-soft); }
.hot-tag-green { color: var(--success); background: var(--success-soft); }
.hot-tag-blue { color: var(--info); background: var(--info-soft); }
.hot-tag-gray { color: var(--text-3); background: var(--surface-3); }
.hot-ico img {
  width: 24px;
  height: 24px;
  object-fit: cover;
  border-radius: 6px;
}
.hot-emoji {
  font-size: 20px;
  line-height: 1;
}
.hot-empty {
  margin: 0;
  padding: 8px 2px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--text-3);
  text-align: center;
}

/* 能力说明卡 */
.capability {
  background: linear-gradient(135deg, var(--ai-soft) 0%, rgba(139, 92, 246, 0.06) 100%);
  border-color: rgba(99, 102, 241, 0.15);
  text-align: center;
}
.cap-ico {
  font-size: 26px;
  margin-bottom: 6px;
}
.cap-text {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--text-2);
}

/* ---------- 右侧对话主区 ---------- */
.ai-chat {
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-l);
  box-shadow: var(--shadow-1);
  overflow: hidden;
  min-height: 600px;
  max-height: calc(100vh - 220px);
}

/* 对话头部条 */
.chat-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border);
  background: var(--surface);
  flex-shrink: 0;
}
.chat-bar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.cb-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--ai-grad);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  box-shadow: 0 4px 12px -3px rgba(99, 102, 241, 0.4);
}
.cb-info {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.cb-name {
  font-size: 15px;
  font-weight: 700;
  color: var(--text);
}
.cb-status {
  font-size: 12px;
  color: var(--success);
  display: flex;
  align-items: center;
  gap: 5px;
}
.cb-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 0 3px var(--success-soft);
  animation: cbPulse 2s ease infinite;
}
@keyframes cbPulse {
  50% { opacity: 0.5; }
}
.chat-bar-right {
  display: flex;
  gap: 4px;
}
.cb-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  font-size: 16px;
  color: var(--text-3);
  background: transparent;
  transition: all 0.16s;
}
.cb-btn:hover {
  background: var(--surface-2);
  color: var(--text);
}

/* 消息列表 */
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  background:
    radial-gradient(circle at 1px 1px, var(--surface-3) 1px, transparent 0);
  background-size: 24px 24px;
}

.msg-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  max-width: 88%;
}
.msg-ai {
  align-self: flex-start;
}
.msg-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--ai-grad);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
  box-shadow: 0 3px 10px -2px rgba(99, 102, 241, 0.4);
}

.msg-bubble-wrap {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.msg-user .msg-bubble-wrap {
  align-items: flex-end;
}

.msg-bubble {
  padding: 12px 16px;
  border-radius: 16px;
  max-width: 100%;
  word-break: break-word;
}
.bubble-ai {
  background: var(--surface);
  border: 1px solid var(--border);
  border-top-left-radius: 4px;
  box-shadow: var(--shadow-1);
}
.bubble-user {
  background: var(--grad-cta);
  color: #fff;
  border-top-right-radius: 4px;
  box-shadow: 0 4px 12px -4px rgba(255, 45, 85, 0.4);
}
.msg-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
}
.msg-time {
  font-size: 11px;
  color: var(--text-3);
  padding: 0 4px;
}

/* 商品推荐卡片 */
.msg-products {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}
.product-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.product-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  transition: all 0.18s var(--ease-out);
  width: 100%;
  max-width: 420px;
}
.product-card:hover {
  border-color: rgba(99, 102, 241, 0.3);
  box-shadow: var(--shadow-2);
  transform: translateY(-2px);
}
.pc-thumb {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 10px;
  background: var(--surface-2);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
}
.pc-ico {
  font-size: 26px;
}
.pc-tag {
  position: absolute;
  top: 4px;
  left: 4px;
  font-size: 9px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 999px;
  color: #fff;
}
.pc-tag-red { background: var(--accent); }
.pc-tag-orange { background: var(--aux); }
.pc-tag-green { background: var(--success); }
.pc-tag-blue { background: var(--info); }
.pc-body {
  flex: 1;
  min-width: 0;
}
.pc-name {
  margin: 0 0 2px;
  font-size: 14px;
  font-weight: 700;
  color: var(--text);
}
.pc-desc {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pc-price-row {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.pc-price {
  font-size: 16px;
  font-weight: 900;
  color: var(--accent);
  font-family: var(--font-num);
}
.pc-origin {
  font-size: 12px;
  color: var(--text-3);
  text-decoration: line-through;
  font-family: var(--font-num);
}
.pc-btn {
  flex-shrink: 0;
  height: 34px;
  padding: 0 16px;
  border-radius: 9px;
  background: var(--grad-cta);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 4px 10px -3px rgba(255, 45, 85, 0.45);
  transition: transform 0.16s, filter 0.16s;
}
.pc-btn:hover {
  filter: brightness(1.06);
  transform: translateY(-1px);
}

/* 正在输入动画 */
.typing-bubble {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 14px 18px;
}
.typing-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ai-primary);
  opacity: 0.5;
  animation: typingBounce 1.2s ease infinite;
}
.typing-dot:nth-child(2) { animation-delay: 0.2s; }
.typing-dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes typingBounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-6px); opacity: 1; }
}

/* 引导提示 */
.chat-hint {
  align-self: center;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  background: var(--ai-soft);
  border: 1px dashed rgba(99, 102, 241, 0.3);
  border-radius: 999px;
  font-size: 13px;
  color: var(--ai-primary);
  font-weight: 600;
  animation: hintFloat 2.5s ease infinite;
}
@keyframes hintFloat {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-4px); }
}
.hint-ico {
  font-size: 15px;
}

/* 输入区 */
.chat-input {
  padding: 14px 20px 16px;
  border-top: 1px solid var(--border);
  background: var(--surface);
  flex-shrink: 0;
}
.input-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 6px 6px 10px;
  border: 1.5px solid var(--border-strong);
  border-radius: 14px;
  background: var(--surface);
  transition: border-color 0.16s, box-shadow 0.16s;
}
.input-bar:focus-within {
  border-color: var(--ai-primary);
  box-shadow: 0 0 0 3px var(--ai-soft);
}
.ib-btn {
  width: 36px;
  height: 36px;
  border-radius: 9px;
  font-size: 17px;
  color: var(--text-3);
  background: transparent;
  transition: all 0.16s;
  flex-shrink: 0;
}
.ib-btn:hover {
  background: var(--surface-2);
  color: var(--ai-primary);
}
.ib-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  padding: 8px 4px;
  min-width: 0;
}
.ib-input::placeholder {
  color: var(--text-3);
}
.ib-send {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  padding: 0 18px;
  border-radius: 10px;
  background: var(--surface-3);
  color: var(--text-3);
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
  transition: all 0.18s var(--ease-out);
}
.ib-send.send-active {
  background: var(--ai-grad);
  color: #fff;
  box-shadow: 0 4px 12px -3px rgba(99, 102, 241, 0.5);
}
.ib-send.send-active:hover {
  filter: brightness(1.08);
  transform: translateY(-1px);
}
.ib-send:disabled {
  cursor: not-allowed;
}
.send-ico {
  font-size: 14px;
}
.input-tip {
  margin: 8px 0 0;
  text-align: center;
  font-size: 11px;
  color: var(--text-3);
}

/* ---------- 响应式 ---------- */
@media (max-width: 980px) {
  .ai-layout {
    grid-template-columns: 240px 1fr;
  }
}
@media (max-width: 820px) {
  .ai-service {
    padding: 22px 16px 70px;
  }
  .ai-hero {
    padding: 28px 22px 26px;
    border-radius: var(--radius-l);
  }
  .hero-title {
    font-size: 24px;
  }
  .hero-stats {
    display: none;
  }
  /* 侧边栏折叠为横向快捷栏 */
  .ai-layout {
    grid-template-columns: 1fr;
  }
  .ai-sidebar {
    flex-direction: column;
  }
  .ai-sidebar .side-section:nth-child(3),
  .ai-sidebar .capability {
    display: none;
  }
  .quick-list {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 8px;
  }
  .quick-item {
    width: auto;
    padding: 8px 12px;
    background: var(--surface-2);
    border-radius: 999px;
    border: 1px solid var(--border);
  }
  .quick-item:hover {
    transform: none;
  }
  .qi-arrow {
    display: none;
  }
  .ai-chat {
    min-height: 500px;
    max-height: calc(100vh - 380px);
  }
}
@media (max-width: 520px) {
  .hero-left {
    gap: 14px;
  }
  .hero-avatar {
    width: 54px;
    height: 54px;
  }
  .avatar-core {
    width: 44px;
    height: 44px;
    font-size: 24px;
  }
  .hero-title {
    font-size: 21px;
  }
  .hero-desc {
    font-size: 13px;
  }
  .chat-bar {
    padding: 12px 14px;
  }
  .cb-info .cb-status {
    display: none;
  }
  .chat-body {
    padding: 14px;
  }
  .msg-row {
    max-width: 95%;
  }
  .product-card {
    flex-wrap: wrap;
    max-width: 100%;
  }
  .pc-btn {
    width: 100%;
    margin-top: 4px;
  }
  .chat-input {
    padding: 12px 14px 14px;
  }
  .ib-send span:last-child {
    display: none;
  }
  .ib-send {
    padding: 0 14px;
  }
}
</style>
