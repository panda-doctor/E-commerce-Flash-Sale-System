<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { uiState } from '../utils/store'
import { fenToYuan } from '../utils/format'
import { toastErr, toastOK, toastWarn } from '../utils/toast'

// 管理控制台：创建演示活动（自动预热）+ 商品主图管理（本地上传 → 回填商品 → 广场展示）+ 既有活动预热
const router = useRouter()

const pad = (n) => String(n).padStart(2, '0')
function fmtLocal(offsetMs) {
  const d = new Date(Date.now() + offsetMs)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/* ---------- ① 创建活动 ---------- */
const form = reactive({
  productId: 1,
  activityName: '机械键盘闪电秒杀',
  startAfterSec: 15,
  durationMin: 30,
  priceYuan: 99,
  stock: 30,
  limitPerUser: 1,
  gotoHome: true,
})
const submitting = ref(false)
const created = ref(null)

async function createAndPreheat() {
  const startTime = fmtLocal(form.startAfterSec * 1000)
  const endTime = fmtLocal(form.startAfterSec * 1000 + form.durationMin * 60 * 1000)
  const body = {
    productId: Number(form.productId),
    activityName: form.activityName.trim(),
    startTime,
    endTime,
    seckillPrice: Math.round(form.priceYuan * 100),
    seckillStock: Number(form.stock),
    limitPerUser: Number(form.limitPerUser),
  }
  if (!body.activityName || !(body.seckillStock > 0)) {
    toastWarn('请填写活动名称与有效库存')
    return
  }
  submitting.value = true
  try {
    const r = await api.createActivity(body)
    const id = r.activityId
    try {
      await api.preheat(id)
      toastOK(`活动 #${id} 已创建并预热，库存 ${form.stock} 就绪`)
    } catch (e) {
      toastWarn(`活动已创建 #${id}，但预热失败：${e.message}（可稍后在下方重试预热）`)
    }
    created.value = id
    uiState.setActivity(id)
    if (form.gotoHome) router.push('/')
  } catch (e) {
    toastErr(e.message || '创建活动失败')
  } finally {
    submitting.value = false
  }
}

/* ---------- ② 商品主图管理 ---------- */
const prodPanel = reactive({
  productId: 1,
  loaded: false,
  name: '',
  description: '',
  priceYuan: 0,
  totalStock: 0,
  imageUrl: '',
  status: 'ON_SHELF',
})
const loadingProd = ref(false)
const uploading = ref(false)
const saving = ref(false)
const fileInput = ref(null)

// 演示商品库（快捷载入）
const demoProducts = [
  { id: 1, name: '机械键盘', emoji: '⌨️', tag: '热卖' },
  { id: 2, name: '无线蓝牙耳机', emoji: '🎧', tag: '新货' },
  { id: 3, name: '智能运动手环', emoji: '⌚', tag: '折扣' },
  { id: 4, name: '高清显示器', emoji: '🖥', tag: '热卖' },
  { id: 5, name: '游戏手柄', emoji: '🎮', tag: '限免' },
  { id: 6, name: '降噪耳机', emoji: '🎵', tag: '新货' },
]

async function quickLoad(id) {
  prodPanel.productId = id
  await loadProduct()
}

async function loadProduct() {
  const pid = Number(prodPanel.productId)
  if (!Number.isInteger(pid) || pid <= 0) return toastWarn('请输入有效的商品编号')
  loadingProd.value = true
  try {
    const p = await api.getProduct(pid)
    prodPanel.loaded = true
    prodPanel.name = p.name
    prodPanel.description = p.description || ''
    prodPanel.priceYuan = Number(p.originalPrice) / 100
    prodPanel.totalStock = p.totalStock ?? 0
    prodPanel.imageUrl = p.imageUrl || ''
    prodPanel.status = p.status || 'ON_SHELF'
    toastOK(`已载入商品 #${pid}`)
  } catch (e) {
    prodPanel.loaded = false
    toastErr(e.message || '商品不存在')
  } finally {
    loadingProd.value = false
  }
}

function pickImage() {
  if (fileInput.value) fileInput.value.click()
}

async function onFileChange(e) {
  const file = e.target.files?.[0]
  e.target.value = '' // 允许重复选择同一文件
  if (!file) return
  uploading.value = true
  try {
    const r = await api.uploadImage(file)
    prodPanel.imageUrl = r.url
    toastOK(`上传成功（${r.storageType} 存储）`)
  } catch (err) {
    toastErr(err.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

async function saveProduct() {
  if (!prodPanel.loaded) return toastWarn('请先载入商品')
  if (!prodPanel.name.trim()) return toastWarn('商品名称不能为空')
  saving.value = true
  try {
    const body = {
      productId: Number(prodPanel.productId),
      name: prodPanel.name.trim(),
      description: prodPanel.description,
      originalPrice: Math.round(prodPanel.priceYuan * 100),
      totalStock: Number(prodPanel.totalStock),
      status: prodPanel.status,
      imageUrl: prodPanel.imageUrl,
    }
    await api.saveProduct(body)
    toastOK(`商品 #${prodPanel.productId} 已保存，图片将在列表/详情页生效`)
  } catch (e) {
    toastErr(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/* ---------- ③ 既有活动预热 ---------- */
const existingId = ref('')
const preheating = ref(false)

async function preheatExisting() {
  const id = Number(existingId.value)
  if (!Number.isInteger(id) || id <= 0) return toastWarn('请输入有效的活动编号')
  preheating.value = true
  try {
    await api.preheat(id)
    toastOK(`活动 #${id} 预热成功`)
  } catch (e) {
    toastErr(e.message || '预热失败')
  } finally {
    preheating.value = false
  }
}

function openExisting() {
  const id = Number(existingId.value)
  if (Number.isInteger(id) && id > 0) {
    uiState.setActivity(id)
    router.push('/')
  } else toastWarn('请输入有效的活动编号')
}
</script>

<template>
  <div class="admin container">
    <!-- ============ 紧凑标题条：单行 + 内嵌步骤指示 ============ -->
    <header class="admin-bar">
      <div class="bar-left">
        <span class="bar-tag">⚙ ADMIN CONSOLE</span>
        <h1>管理控制台</h1>
        <p>三步完成演示配置：上传主图 → 创建活动 → 广场开抢</p>
      </div>
      <ol class="bar-steps" aria-label="配置步骤">
        <li class="bar-step is-active">
          <span class="bar-num">1</span>
          <span class="bar-name">上传主图</span>
        </li>
        <li class="bar-sep"></li>
        <li class="bar-step">
          <span class="bar-num">2</span>
          <span class="bar-name">创建活动</span>
        </li>
        <li class="bar-sep"></li>
        <li class="bar-step">
          <span class="bar-num">3</span>
          <span class="bar-name">广场开抢</span>
        </li>
      </ol>
    </header>

    <!-- ============ 顶部双列：① 主图管理（含演示库填空） + ② 创建活动 ============ -->
    <div class="top-grid">
      <!-- ① 商品主图管理 -->
      <section class="panel prod-panel">
        <div class="phead">
          <div class="phead-l">
            <span class="pnum">01</span>
            <div class="phead-t">
              <h2>商品主图管理</h2>
              <span class="phead-s">本地上传 → 切存储可走 OSS</span>
            </div>
          </div>
          <span class="pill pill-soft">主图</span>
        </div>

        <!-- 商品未载入时：演示商品库填充左侧空白 -->
        <div v-if="!prodPanel.loaded" class="prod-empty">
          <div class="empty-h">
            <span class="empty-h-ico">📦</span>
            <div>
              <h4>请先载入或选择商品</h4>
              <p>输入商品编号并点击「载入商品」，或下方点击演示商品快捷载入。</p>
            </div>
          </div>
          <div class="load-row">
            <label class="f-item">
              <span>商品编号</span>
              <div class="inline">
                <input
                  v-model.number="prodPanel.productId"
                  type="number"
                  min="1"
                  class="field"
                  @keydown.enter="loadProduct"
                />
                <button class="btn btn-outline btn-sm" :disabled="loadingProd" @click="loadProduct">
                  <span v-if="loadingProd" class="spin-sm"></span>
                  {{ loadingProd ? '载入中…' : '载入商品' }}
                </button>
              </div>
            </label>
          </div>
          <div class="demo-lib">
            <div class="demo-lib-h">
              <span class="dot"></span>
              <span>演示商品库 · 一键载入</span>
            </div>
            <div class="demo-grid">
              <button
                v-for="d in demoProducts"
                :key="d.id"
                class="demo-item"
                @click="quickLoad(d.id)"
              >
                <span class="demo-emoji">{{ d.emoji }}</span>
                <div class="demo-meta">
                  <span class="demo-name">{{ d.name }}</span>
                  <span class="demo-id">#{{ d.id }}</span>
                </div>
                <span class="demo-tag">{{ d.tag }}</span>
              </button>
            </div>
          </div>
          <!-- 提示条提到 prod-empty 直接子项，margin-top: auto 才会贴到 panel 底部 -->
          <div class="demo-foot">
            <div class="demo-foot-tip">
              <span class="foot-ico">💡</span>
              <span>载入后即可上传主图、修改商品信息并保存到本系统</span>
            </div>
            <button class="btn btn-ghost btn-sm" @click="router.push('/')">
              去广场查看场次 →
            </button>
          </div>
        </div>

        <!-- 载入后：主图 + 字段 -->
        <div v-else class="prod-body">
          <div class="thumb-zone" :class="{ 'is-uploading': uploading }" @click="pickImage">
            <img v-if="prodPanel.imageUrl" :src="prodPanel.imageUrl" alt="商品主图" />
            <div v-else class="thumb-empty">
              <span class="thumb-empty-ico">🖼</span>
              <span class="thumb-empty-txt">点击上传</span>
              <span class="thumb-empty-sub">JPEG · PNG · WEBP</span>
            </div>
            <div v-if="uploading" class="thumb-mask">
              <span class="spin-md"></span>
              <span>上传中…</span>
            </div>
            <span class="thumb-edit" v-if="!uploading">更换图片</span>
          </div>

          <div class="prod-fields">
            <div class="prod-h">
              <span class="prod-h-id">商品 #{{ prodPanel.productId }}</span>
              <button class="linklike-sm" @click="prodPanel.loaded = false">← 切换商品</button>
            </div>
            <label class="f-item">
              <span>商品名称</span>
              <input v-model="prodPanel.name" type="text" class="field" />
            </label>
            <label class="f-item">
              <span>商品描述</span>
              <input v-model="prodPanel.description" type="text" class="field" placeholder="选填" />
            </label>
            <div class="field-row">
              <label class="f-item">
                <span>原价（元）</span>
                <input v-model.number="prodPanel.priceYuan" type="number" min="0" step="0.01" class="field" />
              </label>
              <label class="f-item">
                <span>总库存（件）</span>
                <input v-model.number="prodPanel.totalStock" type="number" min="0" class="field" />
              </label>
            </div>
            <input
              ref="fileInput"
              type="file"
              accept="image/jpeg,image/png,image/webp,image/gif"
              hidden
              @change="onFileChange"
            />
            <div class="prod-actions">
              <button class="btn btn-cta btn-sm" :disabled="uploading" @click="pickImage">
                <span v-if="uploading" class="spin-sm"></span>
                {{ uploading ? '上传中…' : '上传主图' }}
              </button>
              <button class="btn btn-outline btn-sm" :disabled="saving" @click="saveProduct">
                <span v-if="saving" class="spin-sm"></span>
                {{ saving ? '保存中…' : '保存商品' }}
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- ② 新建秒杀活动 -->
      <section class="panel form-panel">
        <div class="phead">
          <div class="phead-l">
            <span class="pnum pnum-hot">02</span>
            <div class="phead-t">
              <h2>新建秒杀活动</h2>
              <span class="phead-s">配置参数后自动预热库存</span>
            </div>
          </div>
          <span class="pill pill-hot">秒杀</span>
        </div>

        <form class="frm" @submit.prevent="createAndPreheat">
          <label class="f-item full">
            <span>活动名称</span>
            <input
              v-model="form.activityName"
              type="text"
              class="field"
              placeholder="如：机械键盘闪电秒杀"
            />
          </label>

          <div class="frm-group">
            <span class="group-label">基本信息</span>
            <div class="group-body">
              <label class="f-item">
                <span>商品编号</span>
                <input v-model.number="form.productId" type="number" min="1" class="field" />
              </label>
              <label class="f-item">
                <span>每人限购</span>
                <input v-model.number="form.limitPerUser" type="number" min="1" class="field" />
              </label>
            </div>
          </div>

          <div class="frm-group">
            <span class="group-label">价格与库存</span>
            <div class="group-body">
              <label class="f-item">
                <span>秒杀价（元）</span>
                <input v-model.number="form.priceYuan" type="number" min="0.01" step="0.01" class="field" />
              </label>
              <label class="f-item">
                <span>秒杀库存（件）</span>
                <input v-model.number="form.stock" type="number" min="1" class="field" />
              </label>
            </div>
          </div>

          <div class="frm-group">
            <span class="group-label">时间配置</span>
            <div class="group-body">
              <label class="f-item">
                <span>开抢倒计时（秒）</span>
                <input v-model.number="form.startAfterSec" type="number" min="5" class="field" />
              </label>
              <label class="f-item">
                <span>持续时长（分钟）</span>
                <input v-model.number="form.durationMin" type="number" min="1" class="field" />
              </label>
            </div>
          </div>

          <label class="chk">
            <input v-model="form.gotoHome" type="checkbox" />
            <span>创建成功后自动前往活动广场</span>
          </label>

          <button class="btn btn-cta submit" type="submit" :disabled="submitting">
            <span class="submit-shine"></span>
            <span v-if="submitting" class="spin-sm"></span>
            <span v-else class="submit-ico">⚡</span>
            {{ submitting ? '创建与预热中…' : '创建并预热活动' }}
          </button>
        </form>

        <div v-if="created" class="recent">
          <span class="recent-ico">✓</span>
          <span>最近创建：活动 <b class="hnum">#{{ created }}</b></span>
          <button class="linklike" @click="router.push('/')">前往广场开抢 →</button>
        </div>
      </section>
    </div>

    <!-- ============ 底部信息带：4 个紧凑卡片横铺 ============ -->
    <section class="info-strip">
      <!-- ③ 预热既有活动 -->
      <div class="strip-card">
        <div class="strip-h">
          <span class="pnum pnum-blue">03</span>
          <h3>预热既有活动</h3>
        </div>
        <div class="preheat-box">
          <input
            v-model="existingId"
            type="number"
            class="field preheat-input"
            placeholder="活动编号，如 100"
            @keydown.enter="preheatExisting"
          />
          <button class="btn btn-cta preheat-btn" :disabled="preheating" @click="preheatExisting">
            <span v-if="preheating" class="spin-sm"></span>
            {{ preheating ? '预热中…' : '立即预热' }}
          </button>
        </div>
        <button class="btn btn-ghost btn-sm open-square" @click="openExisting">
          在广场打开此活动 →
        </button>
      </div>

      <!-- ④ 小贴士 - 单列四行紧凑信息 -->
      <div class="strip-card">
        <div class="strip-h">
          <span class="pnum pnum-orange">04</span>
          <h3>演示小贴士</h3>
        </div>
        <div class="strip-list">
          <div class="strip-row">
            <span class="row-ico">👤</span>
            <div class="row-t">
              <b>多用户切换</b>
              <span>顶部「演示用户」可切换身份，模拟不同用户抢购</span>
            </div>
          </div>
          <div class="strip-row">
            <span class="row-ico">🔒</span>
            <div class="row-t">
              <b>令牌防重</b>
              <span>同一用户对同一活动只成功一次，重复点击返回"已参与"</span>
            </div>
          </div>
          <div class="strip-row">
            <span class="row-ico">⚡</span>
            <div class="row-t">
              <b>限流防护</b>
              <span>点击过快触发限流（60 秒窗口 5 次），属预期防护行为</span>
            </div>
          </div>
          <div class="strip-row">
            <span class="row-ico">☁️</span>
            <div class="row-t">
              <b>存储切换</b>
              <span>改后端 <code>storage.type=oss</code> 即可切到阿里云 OSS</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ⑤ 系统状态 / 本次会话 -->
      <div class="strip-card">
        <div class="strip-h">
          <span class="pnum pnum-green">05</span>
          <h3>系统状态</h3>
        </div>
        <div class="stat-list">
          <div class="stat-row">
            <span class="stat-k">API 健康</span>
            <span class="stat-v ok">● 在线</span>
          </div>
          <div class="stat-row">
            <span class="stat-k">缓存服务</span>
            <span class="stat-v ok">● Redis</span>
          </div>
          <div class="stat-row">
            <span class="stat-k">存储引擎</span>
            <span class="stat-v">本地 / OSS</span>
          </div>
          <div class="stat-row">
            <span class="stat-k">限流阈值</span>
            <span class="stat-v">5 次 / 60 s</span>
          </div>
          <div class="stat-row">
            <span class="stat-k">令牌有效期</span>
            <span class="stat-v">30 分钟</span>
          </div>
          <div class="stat-row">
            <span class="stat-k">演示用户 ID</span>
            <span class="stat-v hnum">#1001</span>
          </div>
        </div>
      </div>

      <!-- ⑥ 快捷文档 / 帮助 -->
      <div class="strip-card">
        <div class="strip-h">
          <span class="pnum pnum-purple">06</span>
          <h3>快速参考</h3>
        </div>
        <ul class="ref-list">
          <li>
            <span class="ref-k">活动</span>
            <code>POST /api/activity/create</code>
          </li>
          <li>
            <span class="ref-k">预热</span>
            <code>POST /api/activity/{id}/preheat</code>
          </li>
          <li>
            <span class="ref-k">秒杀</span>
            <code>POST /api/seckill/{id}</code>
          </li>
          <li>
            <span class="ref-k">订单</span>
            <code>GET /api/order/{orderNo}</code>
          </li>
          <li>
            <span class="ref-k">主图上传</span>
            <code>POST /api/upload/image</code>
          </li>
          <li>
            <span class="ref-k">商品</span>
            <code>GET /api/product/{id}</code>
          </li>
        </ul>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ============================================================
   管理控制台 · 紧凑重构 v5
   - 顶部双列 stretch 等高 + 主图面板 demo-foot 填空白
   - CTA 按钮升级：⚡ 图标 + 渐变高光 + 上浮阴影
   - 整体 padding 收紧，消除中间空白带
   ============================================================ */
.admin {
  padding: 14px 22px 28px;
}

/* ============================================================
   紧凑标题条（替代之前的深色大 Hero）
   ============================================================ */
.admin-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 14px 22px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #0d0e14 0%, #1a0f1e 100%);
  border-radius: var(--radius-l);
  color: #fff;
  box-shadow: 0 4px 8px -2px rgba(0, 0, 0, 0.05), 0 12px 28px -8px rgba(0, 0, 0, 0.12);
  position: relative;
  overflow: hidden;
}
.admin-bar::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 80% 50%, rgba(255, 45, 85, 0.28), transparent 60%),
    radial-gradient(circle at 15% 50%, rgba(255, 149, 0, 0.15), transparent 55%);
  pointer-events: none;
}
.bar-left {
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  z-index: 1;
  flex: 1;
  min-width: 0;
}
.bar-tag {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 2px;
  color: rgba(255, 255, 255, 0.5);
  padding: 5px 10px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}
.bar-left h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 900;
  background: linear-gradient(120deg, #fff 0%, #ffd0d8 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 0.5px;
}
.bar-left p {
  margin: 0;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 步骤指示器（紧凑胶囊） */
.bar-steps {
  display: flex;
  align-items: center;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}
.bar-step {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.08);
  transition: all 0.2s;
}
.bar-num {
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  color: rgba(255, 255, 255, 0.7);
  font-size: 10px;
  font-weight: 800;
  font-family: var(--font-num);
}
.bar-name {
  font-size: 12px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.75);
  white-space: nowrap;
}
.bar-step.is-active {
  background: rgba(255, 45, 85, 0.18);
  border-color: rgba(255, 45, 85, 0.35);
}
.bar-step.is-active .bar-num {
  background: linear-gradient(120deg, #ff2d55 0%, #ff5e3a 100%);
  color: #fff;
}
.bar-step.is-active .bar-name {
  color: #fff;
}
.bar-sep {
  width: 22px;
  height: 1px;
  margin: 0 4px;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.25), rgba(255, 255, 255, 0.06));
  position: relative;
}
.bar-sep::after {
  content: '';
  position: absolute;
  right: -1px;
  top: 50%;
  transform: translateY(-50%);
  border: 3px solid transparent;
  border-left-color: rgba(255, 255, 255, 0.22);
}

/* ============================================================
   顶部双列（强制 stretch 等高对齐）
   ============================================================ */
.top-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
  margin-bottom: 14px;
  align-items: stretch;
}
.top-grid > .panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
.top-grid > .panel > .phead { flex-shrink: 0; }

/* ============================================================
   通用面板
   ============================================================ */
.panel {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-l);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04), 0 1px 3px rgba(0, 0, 0, 0.06);
}
.prod-panel,
.form-panel {
  padding: 16px 18px;
}

/* 统一 phead */
.phead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.phead-l {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.phead-t {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  min-width: 0;
}
.phead-t h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  color: var(--text);
}
.phead-s {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-3);
}
.pnum {
  font-size: 12px;
  font-weight: 900;
  color: var(--accent);
  background: var(--accent-soft);
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-family: var(--font-num);
  flex-shrink: 0;
}
.pnum.pnum-hot { color: #fff; background: linear-gradient(135deg, #ff2d55 0%, #ff5e3a 100%); }
.pnum.pnum-blue { color: #fff; background: linear-gradient(135deg, #2563eb 0%, #06b6d4 100%); }
.pnum.pnum-orange { color: #fff; background: linear-gradient(135deg, #ff9500 0%, #fb923c 100%); }
.pnum.pnum-green { color: #fff; background: linear-gradient(135deg, #10b981 0%, #34d399 100%); }
.pnum.pnum-purple { color: #fff; background: linear-gradient(135deg, #7c3aed 0%, #a855f7 100%); }

/* 表单基础 */
.f-item > span {
  display: block;
  font-size: 12px;
  color: var(--text-2);
  font-weight: 600;
  margin-bottom: 4px;
}
.f-item.full {
  width: 100%;
}
.f-item input {
  width: 100%;
}
.inline {
  display: flex;
  gap: 8px;
}
.inline .field {
  flex: 1;
  min-width: 0;
}

/* ============================================================
   ① 商品主图管理（演示库填空）
   ============================================================ */
.prod-empty {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1 1 auto;
  min-height: 0;
}
.demo-foot {
  flex-shrink: 0;
}
.empty-h {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: var(--surface-2);
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-m);
}
.empty-h-ico {
  font-size: 22px;
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  flex-shrink: 0;
}
.empty-h h4 {
  margin: 0 0 1px;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--text);
}
.empty-h p {
  margin: 0;
  font-size: 12px;
  color: var(--text-2);
  line-height: 1.4;
}
.load-row .f-item {
  width: 100%;
}

/* 演示商品库网格 */
.demo-lib {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  padding: 12px 14px 14px;
  background: linear-gradient(135deg, var(--surface-2) 0%, var(--bg-2) 100%);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
  min-height: 0;
}
.demo-lib-h {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-2);
  margin-bottom: 9px;
}
.demo-lib-h .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
}
.demo-grid {
  flex: 1 1 auto;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  align-content: start;
}
.demo-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 9px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.18s var(--ease-out);
  text-align: left;
  min-height: 0;
}
.demo-item:hover {
  border-color: var(--accent);
  background: var(--accent-soft);
  transform: translateY(-1px);
}
.demo-emoji {
  font-size: 18px;
  flex-shrink: 0;
}
.demo-meta {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  line-height: 1.2;
}
.demo-name {
  font-size: 12px;
  font-weight: 700;
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.demo-id {
  font-size: 10px;
  color: var(--text-3);
  font-family: var(--font-num);
}
.demo-tag {
  font-size: 9.5px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 4px;
  background: var(--accent-soft);
  color: var(--accent);
  white-space: nowrap;
}

/* 演示库底栏（填空白） */
.demo-foot {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  background: linear-gradient(135deg, rgba(255, 45, 85, 0.06) 0%, rgba(255, 94, 58, 0.04) 100%);
  border: 1px solid rgba(255, 45, 85, 0.12);
  border-radius: 10px;
}
.demo-foot-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: var(--text-2);
  line-height: 1.4;
}
.foot-ico {
  font-size: 14px;
  flex-shrink: 0;
}

/* 载入后 */
.prod-body {
  display: grid;
  grid-template-columns: 168px 1fr;
  gap: 14px;
  align-items: start;
}
.prod-h {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.prod-h-id {
  font-size: 12px;
  font-weight: 800;
  color: var(--accent);
  background: var(--accent-soft);
  padding: 3px 8px;
  border-radius: 6px;
}
.linklike-sm {
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 11.5px;
  color: var(--text-3);
  padding: 2px 0;
}
.linklike-sm:hover { color: var(--accent); }
.thumb-zone {
  position: relative;
  width: 168px;
  height: 168px;
  border-radius: var(--radius-m);
  overflow: hidden;
  cursor: pointer;
  border: 2px dashed var(--border-strong);
  background: var(--surface-2);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.thumb-zone:hover {
  border-color: var(--accent);
  box-shadow: 0 0 0 4px rgba(255, 45, 85, 0.1);
}
.thumb-zone.is-uploading {
  cursor: progress;
}
.thumb-zone img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s var(--ease-out);
}
.thumb-zone:hover img {
  transform: scale(1.04);
}
.thumb-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  color: var(--text-3);
}
.thumb-empty-ico {
  font-size: 28px;
}
.thumb-empty-txt {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-2);
}
.thumb-empty-sub {
  font-size: 10px;
  letter-spacing: 0.5px;
}
.thumb-mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.thumb-edit {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  font-size: 10.5px;
  font-weight: 600;
  padding: 3px 9px;
  border-radius: 999px;
  backdrop-filter: blur(6px);
  opacity: 0;
  transition: opacity 0.2s;
}
.thumb-zone:hover .thumb-edit {
  opacity: 1;
}

.prod-fields {
  display: flex;
  flex-direction: column;
  gap: 9px;
}
.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.prod-actions {
  display: flex;
  gap: 7px;
  margin-top: 2px;
}

/* ============================================================
   ② 创建活动表单
   ============================================================ */
.frm {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1 1 auto;
  min-height: 0;
}
.frm-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 11px 13px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-m);
}
.group-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 10.5px;
  font-weight: 800;
  letter-spacing: 1px;
  color: var(--text-3);
  text-transform: uppercase;
}
.group-label::before {
  content: '';
  width: 3px;
  height: 12px;
  background: linear-gradient(120deg, #ff2d55 0%, #ff5e3a 100%);
  border-radius: 999px;
}
.group-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 9px;
}
.frm .f-item.full input {
  width: 100%;
}
.chk {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  padding: 2px 0;
}
.chk input {
  width: 16px;
  height: 16px;
  accent-color: var(--accent);
  cursor: pointer;
}
.submit {
  position: relative;
  overflow: hidden;
  height: 50px;
  font-size: 14.5px;
  font-weight: 800;
  letter-spacing: 2px;
  margin-top: auto;
  box-shadow: 0 8px 24px -8px rgba(255, 45, 85, 0.5), 0 4px 8px -2px rgba(255, 45, 85, 0.25);
}
.submit:hover:not(:disabled) {
  box-shadow: 0 12px 28px -10px rgba(255, 45, 85, 0.65), 0 6px 12px -4px rgba(255, 45, 85, 0.35);
  transform: translateY(-1px);
}
.submit-ico {
  font-size: 16px;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.3));
}
.submit-shine {
  position: absolute;
  inset: 0;
  background: linear-gradient(105deg, transparent 30%, rgba(255, 255, 255, 0.18) 50%, transparent 70%);
  background-size: 220% 100%;
  background-position: 200% 0;
  pointer-events: none;
  animation: shine-sweep 3.2s ease-in-out infinite;
}
@keyframes shine-sweep {
  0% { background-position: 200% 0; }
  100% { background-position: -120% 0; }
}
.spin-sm {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2.5px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  animation: rot 0.7s linear infinite;
}
.spin-md {
  display: inline-block;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 3px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  animation: rot 0.7s linear infinite;
}
@keyframes rot {
  to { transform: rotate(360deg); }
}

.recent {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13.5px;
  color: var(--text-2);
  background: rgba(52, 199, 89, 0.1);
  border: 1px solid rgba(52, 199, 89, 0.18);
  border-radius: 10px;
  padding: 9px 12px;
}
.recent-ico {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--success);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}
.linklike {
  margin-left: auto;
  color: var(--success);
  font-weight: 800;
  text-decoration: underline;
  cursor: pointer;
  background: transparent;
  border: 0;
}

/* 底部信息带：4 卡横向 */
.info-strip {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.05fr) minmax(0, 0.85fr) minmax(0, 1.1fr);
  gap: 12px;
  align-items: stretch;
}
.info-strip > .strip-card {
  display: flex;
  flex-direction: column;
}
.info-strip > .strip-card > .strip-list {
  flex: 1;
}
.strip-card {
  padding: 13px 15px 15px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-l);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04), 0 1px 3px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  gap: 9px;
}
.strip-h {
  display: flex;
  align-items: center;
  gap: 8px;
}
.strip-h h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 800;
  color: var(--text);
}

/* 预热 */
.preheat-box {
  display: flex;
  gap: 8px;
}
.preheat-input { flex: 1; min-width: 0; }
.preheat-btn { flex-shrink: 0; min-width: 96px; padding: 0 12px; }
.open-square {
  width: 100%;
  padding: 7px 12px;
}

/* 小贴士列表 */
.strip-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.strip-row {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--border);
}
.strip-row:last-child { border-bottom: 0; }
.row-ico {
  font-size: 15px;
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 6px;
  margin-top: 1px;
}
.row-t {
  display: flex;
  flex-direction: column;
  gap: 1px;
  flex: 1;
  min-width: 0;
  line-height: 1.4;
}
.row-t b {
  font-size: 12px;
  font-weight: 700;
  color: var(--text);
}
.row-t span {
  font-size: 11.5px;
  color: var(--text-2);
}
.row-t code {
  background: var(--surface-2);
  padding: 0 4px;
  border-radius: 3px;
  font-family: var(--font-num);
  font-size: 10.5px;
  color: var(--accent);
}

/* 状态列表 */
.stat-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.stat-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  padding: 4px 0;
  border-bottom: 1px dashed var(--border);
}
.stat-row:last-child { border-bottom: 0; }
.stat-k {
  color: var(--text-2);
}
.stat-v {
  font-weight: 700;
  color: var(--text);
  font-family: var(--font-num);
  font-size: 11.5px;
}
.stat-v.ok { color: var(--success); }

/* 接口参考 */
.ref-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.ref-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  padding: 4px 0;
  border-bottom: 1px dashed var(--border);
}
.ref-list li:last-child { border-bottom: 0; }
.ref-k {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
  color: var(--text-2);
  padding: 2px 7px;
  background: var(--surface-2);
  border-radius: 5px;
  width: 56px;
  text-align: center;
}
.ref-list code {
  flex: 1;
  font-family: var(--font-num);
  font-size: 10.5px;
  color: var(--text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 1100px) {
  .top-grid {
    grid-template-columns: 1fr;
  }
  .info-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 820px) {
  .admin-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
    padding: 14px 16px;
  }
  .bar-left {
    flex-wrap: wrap;
    gap: 12px;
  }
  .bar-left p { display: none; }
  .bar-steps {
    width: 100%;
    justify-content: space-between;
  }
  .bar-sep { flex: 1; width: auto; min-width: 12px; }
  .info-strip {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 640px) {
  .admin { padding: 14px 12px 50px; }
  .prod-panel,
  .form-panel { padding: 16px; }
  .prod-body { grid-template-columns: 1fr; }
  .thumb-zone { width: 100%; height: 200px; }
  .demo-grid { grid-template-columns: repeat(2, 1fr); }
  .field-row,
  .group-body { grid-template-columns: 1fr; }
  .preheat-box { flex-direction: column; }
  .preheat-btn { width: 100%; }
}
</style>
