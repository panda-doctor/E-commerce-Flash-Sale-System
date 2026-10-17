/**
 * 真实浏览器核查脚本（仅本地验证用，不参与构建）。
 * 用系统已安装的 Chrome，采集控制台异常、页面异常、失败请求与关键页面断言。
 *
 * 用法：node scripts/browser-check.mjs
 * 注意：只做只读浏览与登录，不点击"立即秒杀"，避免产生真实订单/扣减库存。
 */
import { chromium } from 'playwright-core'

const BASE = process.env.CHECK_BASE_URL ?? 'http://127.0.0.1:5173'
const CHROME = process.env.CHROME_PATH ?? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const ACCOUNT = process.env.CHECK_ACCOUNT ?? ''
const PASSWORD = process.env.CHECK_PASSWORD ?? ''

const browser = await chromium.launch({ executablePath: CHROME, headless: true })
const context = await browser.newContext({ viewport: { width: 1280, height: 900 } })
const page = await context.newPage()

const issues = []
page.on('console', (message) => {
  if (message.type() === 'error') issues.push({ kind: 'console', text: message.text() })
})
page.on('pageerror', (error) => issues.push({ kind: 'pageerror', text: String(error) }))
page.on('requestfailed', (request) =>
  issues.push({ kind: 'requestfailed', url: request.url(), text: request.failure()?.errorText ?? '' }),
)
page.on('response', (response) => {
  if (response.status() >= 400) issues.push({ kind: 'http', url: response.url(), status: response.status() })
})

const report = {}
const startIssues = () => issues.length

try {
  // 1) 首页
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('a[href^="/products/"]', { timeout: 20000 })
  report.home = {
    cards: await page.locator('article, a[href^="/products/"]').count(),
    totalText: (await page.getByText(/共 \d+ 件商品/).first().textContent()) ?? '',
  }

  // 2) 搜索
  await page.goto(`${BASE}/products`, { waitUntil: 'domcontentloaded' })
  await page.getByLabel('搜索商品').fill('P4D4')
  await page.waitForTimeout(1200)
  report.search = { totalText: (await page.getByText(/共 \d+ 件商品/).first().textContent()) ?? '' }
  await page.getByLabel('清空搜索').click()
  await page.waitForTimeout(1200)
  report.searchCleared = { totalText: (await page.getByText(/共 \d+ 件商品/).first().textContent()) ?? '' }

  // 3) 活动详情（不点秒杀）
  await page.goto(`${BASE}/activities`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('a[href^="/activities/"]', { timeout: 20000 })
  await page.locator('a[href^="/activities/"]').first().click()
  await page.waitForSelector('text=抢购榜 TOP 10', { timeout: 20000 })
  const buyButton = page.getByRole('button', { name: /立即秒杀|未开始|已结束|已售罄|暂不可/ }).first()
  report.activityDetail = {
    url: page.url(),
    title: (await page.locator('h1').first().textContent()) ?? '',
    buttonText: (await buyButton.textContent()) ?? '',
    buttonDisabled: await buyButton.isDisabled(),
  }

  // 3.5) 商品详情：应能匹配到该商品的秒杀活动（曾因 number/string === 恒 false 而永远不显示）
  const firstActivity = await page.evaluate(async () => {
    const response = await fetch('/api/seckill/activities')
    const body = await response.json()
    return body?.data?.[0] ?? null
  })
  if (firstActivity?.productId) {
    await page.goto(`${BASE}/products/${firstActivity.productId}`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(1500)
    const text = await page.locator('body').innerText()
    report.productDetail = {
      url: page.url(),
      hasActivityBadge: text.includes('有秒杀活动'),
      hasNoActivityHint: text.includes('没有进行中的秒杀活动'),
      goSeckillLink: await page.getByRole('link', { name: '去秒杀' }).count(),
    }
  }

  // 4) 未登录 → 受保护页跳登录
  await page.goto(`${BASE}/orders`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  report.ordersUnauth = { url: page.url() }

  // 5) 登录
  if (ACCOUNT && PASSWORD) {
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
    await page.fill('input[name="accountName"]', ACCOUNT)
    await page.fill('input[name="password"]', PASSWORD)
    await page.getByRole('button', { name: /登录/ }).first().click()
    await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 20000 })
    await page.waitForTimeout(1200)
    const header = await page.locator('header').innerText()
    report.login = { url: page.url(), navShowsUser: /p4d6|P4D6/.test(header) }

    // 6) 我的订单 + 订单详情（只读）
    await page.goto(`${BASE}/orders`, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(1500)
    const orderLinks = page.locator('a[href^="/orders/"]')
    report.ordersAuthed = { url: page.url(), orderLinks: await orderLinks.count() }
    if ((await orderLinks.count()) > 0) {
      const before = startIssues()
      await orderLinks.first().click()
      await page.waitForSelector('text=订单详情', { timeout: 20000 })
      await page.waitForTimeout(1200)
      report.orderDetail = {
        url: page.url(),
        badge: (await page.locator('dl').first().innerText()).slice(0, 60),
        newIssues: issues.slice(before).filter((item) => item.status !== 404).length,
      }
    }
  }

  // 7) 管理端闸门（无令牌应展示输入框而不是崩溃）
  await page.goto(`${BASE}/admin`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(1200)
  report.adminGate = {
    bodyText: (await page.locator('body').innerText()).slice(0, 80).replace(/\n/g, ' '),
  }

  // 8) 无效路由
  await page.goto(`${BASE}/not-a-real-page`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(600)
  report.notFound = { bodyText: (await page.locator('body').innerText()).slice(0, 60).replace(/\n/g, ' ') }

  // 9) 移动端抽屉导航
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' })
  await page.waitForTimeout(800)
  const burger = page.getByRole('button', { name: '打开导航菜单' })
  report.mobile = { burgerVisible: await burger.isVisible() }
  if (await burger.isVisible()) {
    await burger.click()
    await page.waitForTimeout(500)
    report.mobile.drawerLinks = await page.locator('nav[aria-label="移动端导航"] a').count()
    report.mobile.drawerText = (await page.locator('[role="dialog"]').first().innerText()).slice(0, 60).replace(/\n/g, ' ')
  }
  // 10) 接口失败：拦截商品接口 → 必须有可读错误态与重试按钮，且不白屏
  const errorPage = await context.newPage()
  await errorPage.route('**/api/products**', (route) => route.abort())
  await errorPage.goto(`${BASE}/products`, { waitUntil: 'domcontentloaded' })
  await errorPage.waitForTimeout(4000)
  const errorText = await errorPage.locator('body').innerText()
  report.networkFailure = {
    hasErrorState: /加载失败|网络|超时|稍后/.test(errorText),
    hasRetryButton: await errorPage.getByRole('button', { name: '重新加载' }).count(),
    crashed: errorText.includes('页面出现异常'),
  }
  await errorPage.close()
} catch (error) {
  report.failure = String(error)
} finally {
  report.issues = {
    total: issues.length,
    nonImage404: issues.filter((item) => item.status !== 404 && !/404/.test(item.text ?? '')).slice(0, 5),
    pageErrors: issues.filter((item) => item.kind === 'pageerror').length,
  }
  console.log(JSON.stringify(report, null, 2))
  await browser.close()
}
