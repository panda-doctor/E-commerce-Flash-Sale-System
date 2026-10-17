/**
 * UI 回归核查（真实 Chrome）：多视口截图 + 横向溢出 + 控制台异常 + 关键内容断言。
 *
 * 依赖 playwright-core（未写入 package.json）：npm i --no-save playwright-core
 * 用法：node scripts/ui-check.mjs
 */
import { mkdirSync } from 'node:fs'
import { chromium } from 'playwright-core'

const BASE = process.env.CHECK_BASE_URL ?? 'http://127.0.0.1:5173'
const CHROME = process.env.CHROME_PATH ?? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const SHOT_DIR = process.env.SHOT_DIR ?? 'ui-shots'
const ACCOUNT = process.env.CHECK_ACCOUNT ?? ''
const PASSWORD = process.env.CHECK_PASSWORD ?? ''

const VIEWPORTS = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'laptop', width: 1280, height: 800 },
  { name: 'tablet', width: 834, height: 1112 },
  { name: 'mobile', width: 390, height: 844 },
]

mkdirSync(SHOT_DIR, { recursive: true })

const browser = await chromium.launch({ executablePath: CHROME, headless: true })
const report = { pages: [], errors: [] }

/** 采集单页：溢出、控制台错误、关键元素计数，并截图。 */
async function inspect(page, path, name, { fullPage = false, expect } = {}) {
  const pageErrors = []
  const onError = (error) => pageErrors.push(String(error))
  page.on('pageerror', onError)

  await page.goto(`${BASE}${path}`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)

  const metrics = await page.evaluate(() => {
    const doc = document.documentElement
    // 位于横向滚动/裁剪容器内的元素会正常超出视口右缘，不算页面布局溢出
    const clippedByAncestor = (element) => {
      let node = element.parentElement
      while (node && node !== document.body) {
        const style = getComputedStyle(node)
        if (['auto', 'scroll', 'hidden', 'clip'].includes(style.overflowX)) {
          return true
        }
        node = node.parentElement
      }
      return false
    }

    const overflowing = Array.from(document.querySelectorAll('body *'))
      .filter((element) => {
        const rect = element.getBoundingClientRect()
        if (rect.width <= 0 || rect.right <= window.innerWidth + 1) {
          return false
        }
        return !clippedByAncestor(element)
      })
      .slice(0, 5)
      .map((element) => `${element.tagName.toLowerCase()}.${String(element.className).slice(0, 40)}`)
    return {
      scrollWidth: doc.scrollWidth,
      innerWidth: window.innerWidth,
      overflowing,
      cards: document.querySelectorAll('article').length,
      searchInputs: document.querySelectorAll('input[aria-label="搜索商品"]').length,
      text: document.body.innerText.slice(0, 4000),
    }
  })

  if (name.includes('home') && metrics.searchInputs === 0) {
    report.errors.push(`${name} 首页搜索框缺失`)
  }

  if (expect) {
    for (const [key, value] of Object.entries(expect)) {
      if (!metrics.text.includes(value)) {
        report.errors.push(`${name} 缺少预期内容「${value}」`)
      }
    }
  }

  if (metrics.scrollWidth > metrics.innerWidth + 1 || metrics.overflowing.length > 0) {
    report.errors.push(
      `${name} 横向溢出: scrollWidth=${metrics.scrollWidth} > innerWidth=${metrics.innerWidth} 元素=${metrics.overflowing.join(', ')}`,
    )
  }
  if (pageErrors.length > 0) {
    report.errors.push(`${name} 页面异常: ${pageErrors[0]}`)
  }

  await page.screenshot({ path: `${SHOT_DIR}/${name}.png`, fullPage })
  page.off('pageerror', onError)
  report.pages.push({ name, path, cards: metrics.cards, scrollWidth: metrics.scrollWidth })
}

try {
  for (const viewport of VIEWPORTS) {
    const context = await browser.newContext({ viewport: { width: viewport.width, height: viewport.height } })
    const page = await context.newPage()

    await inspect(page, '/', `${viewport.name}-home`, {
      fullPage: viewport.name === 'desktop',
      expect: { 商品: '在售好物' },
    })
    await inspect(page, '/activities', `${viewport.name}-activities`, { expect: { 活动: '秒杀活动' } })
    await inspect(page, '/login', `${viewport.name}-login`, { expect: { 登录: '登录' } })
    await context.close()
  }

  // 商品详情：取真实活动对应的商品
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await context.newPage()
  // 先落到站点再取数据：about:blank 上相对路径 fetch 会直接失败
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
  const productId = await page
    .evaluate(async () => {
      const response = await fetch('/api/products?page=1&pageSize=1')
      return (await response.json())?.data?.records?.[0]?.productId ?? null
    })
    .catch(() => null)

  if (productId) {
    await inspect(page, `/products/${productId}`, 'desktop-product', {
      fullPage: true,
      expect: { 返回: '返回商品列表' },
    })
  }

  // 有活动关联的商品详情：验证活动卡/秒杀入口的真实布局
  const activityProductId = await page
    .evaluate(async () => {
      const response = await fetch('/api/seckill/activities')
      return (await response.json())?.data?.[0]?.productId ?? null
    })
    .catch(() => null)

  if (activityProductId) {
    await inspect(page, `/products/${activityProductId}`, 'desktop-product-activity', {
      fullPage: true,
      expect: { 返回: '返回商品列表' },
    })
  }

  // 登录后订单页（真实账号由环境变量传入，不落盘）
  if (ACCOUNT && PASSWORD) {
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
    await page.fill('input[name="accountName"]', ACCOUNT)
    await page.fill('input[name="password"]', PASSWORD)
    await page.click('button[type="submit"]')
    await page.waitForTimeout(2500)
    await inspect(page, '/orders', 'desktop-orders', { expect: { 订单: '我的订单' } })

    // 订单页移动端要单独开视口，复用桌面 context 会得到桌面宽度（假溢出）
    const mobileContext = await browser.newContext({
      viewport: { width: 390, height: 844 },
      storageState: await context.storageState(),
    })
    const mobilePage = await mobileContext.newPage()
    await inspect(mobilePage, '/orders', 'mobile-orders', { expect: { 订单: '我的订单' } })
    await mobileContext.close()
  }

  await context.close()
} finally {
  console.log(JSON.stringify(report, null, 2))
  await browser.close()
}
