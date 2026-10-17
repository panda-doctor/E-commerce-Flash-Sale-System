/**
 * 管理端真实浏览器核查（只读）：注入令牌 → 访问 /admin → 选活动 → 看指标面板。
 * 不点击"落库快照/死信重放"等会写数据的按钮。
 *
 * 用法：$env:CHECK_ADMIN_TOKEN='...'; node scripts/admin-check.mjs
 */
import { chromium } from 'playwright-core'

const BASE = process.env.CHECK_BASE_URL ?? 'http://127.0.0.1:5173'
const CHROME = process.env.CHROME_PATH ?? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const TOKEN = process.env.CHECK_ADMIN_TOKEN ?? ''

const browser = await chromium.launch({ executablePath: CHROME, headless: true })
const context = await browser.newContext({ viewport: { width: 1360, height: 900 } })
await context.addInitScript((token) => {
  window.sessionStorage.setItem('flash.admin.token', token)
}, TOKEN)
const page = await context.newPage()

const issues = []
page.on('pageerror', (error) => issues.push({ kind: 'pageerror', text: String(error) }))
page.on('console', (message) => {
  if (message.type() === 'error') issues.push({ kind: 'console', text: message.text() })
})
page.on('response', (response) => {
  if (response.status() >= 400) issues.push({ kind: 'http', url: response.url(), status: response.status() })
})

const report = {}
try {
  await page.goto(`${BASE}/admin`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('text=管理控制台', { timeout: 20000 })
  report.gatePassed = (await page.locator('body').innerText()).includes('已载入管理令牌')
  report.crash = (await page.locator('body').innerText()).includes('页面出现异常')

  const quickPick = page.locator('text=快速选择').locator('..').locator('button').first()
  report.hasActivityPicker = await quickPick.isVisible().catch(() => false)
  if (report.hasActivityPicker) {
    await quickPick.click()
    await page.waitForTimeout(2500)
    const text = await page.locator('body').innerText()
    report.metricsRendered = /订单|库存|成功率|拒绝/.test(text)
    report.metricsSnippet = text.split('\n').filter((line) => /订单|库存|成功率|拒绝/.test(line)).slice(0, 6)
  }
} catch (error) {
  report.failure = String(error)
} finally {
  report.issues = {
    total: issues.length,
    pageErrors: issues.filter((item) => item.kind === 'pageerror').map((item) => item.text),
    http: issues.filter((item) => item.kind === 'http').slice(0, 4),
  }
  console.log(JSON.stringify(report, null, 2))
  await browser.close()
}
