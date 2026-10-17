/**
 * 配色对比度自检（WCAG 2.1）：验证设计令牌常用前景/背景组合。
 * 纯计算，不依赖浏览器。用法：node scripts/contrast-check.mjs
 *
 * 阈值约定：
 * - 承载信息的文字 ≥4.5:1（AA 正文）；
 * - 弱化文字 #98A2B3 只用于占位符/图标/禁用态，不承载关键信息，阈值按 3:1 记录；
 * - 边框等非文字元素不设对比度阈值。
 */

const TOKENS = {
  pageBg: '#F6F7F9',
  surface: '#FFFFFF',
  surfaceMuted: '#F0F2F5',
  textPrimary: '#202124',
  textSecondary: '#5D6675',
  textMuted: '#98A2B3',
  border: '#E5E7EB',
  primary: '#D93A40',
  primaryHover: '#C22F36',
  primaryInk: '#B3252B',
  primarySoft: '#FFF1F0',
  success: '#12724E',
  warning: '#9A5B08',
  danger: '#C42A1F',
}

const PAIRS = [
  ['主文字 / 页面底', TOKENS.textPrimary, TOKENS.pageBg, 4.5],
  ['主文字 / 白卡', TOKENS.textPrimary, TOKENS.surface, 4.5],
  ['主文字 / 次级面', TOKENS.textPrimary, TOKENS.surfaceMuted, 4.5],
  ['次级文字 / 白卡', TOKENS.textSecondary, TOKENS.surface, 4.5],
  ['次级文字 / 页面底', TOKENS.textSecondary, TOKENS.pageBg, 4.5],
  ['次级文字 / 次级面', TOKENS.textSecondary, TOKENS.surfaceMuted, 4.5],
  // 仅用于 placeholder / 图标 / 禁用态，不承载信息，故不设正文阈值
  ['弱化文字 / 白卡（仅占位/图标）', TOKENS.textMuted, TOKENS.surface, 0],
  ['白字 / 秒杀红按钮', TOKENS.surface, TOKENS.primary, 4.5],
  ['白字 / 红按钮 hover', TOKENS.surface, TOKENS.primaryHover, 4.5],
  ['秒杀红文字 / 白卡', TOKENS.primary, TOKENS.surface, 4.5],
  ['浅红底小字 / 浅红底', TOKENS.primaryInk, TOKENS.primarySoft, 4.5],
  ['成功色 / 白卡', TOKENS.success, TOKENS.surface, 4.5],
  ['成功色 / 成功浅底(10%)', TOKENS.success, mix(TOKENS.success, 0.1), 4.5],
  ['警告色 / 白卡', TOKENS.warning, TOKENS.surface, 4.5],
  ['警告色 / 警告浅底(10%)', TOKENS.warning, mix(TOKENS.warning, 0.1), 4.5],
  ['危险色 / 白卡', TOKENS.danger, TOKENS.surface, 4.5],
  ['危险色 / 危险浅底(10%)', TOKENS.danger, mix(TOKENS.danger, 0.1), 4.5],
  ['白字 / 深色底', TOKENS.surface, TOKENS.textPrimary, 4.5],
  ['边框 / 页面底（非文字）', TOKENS.border, TOKENS.pageBg, 0],
]

function channel(value) {
  const c = value / 255
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
}

function luminance(hex) {
  const clean = hex.replace('#', '')
  const r = parseInt(clean.slice(0, 2), 16)
  const g = parseInt(clean.slice(2, 4), 16)
  const b = parseInt(clean.slice(4, 6), 16)
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

function ratio(a, b) {
  const la = luminance(a)
  const lb = luminance(b)
  const [high, low] = la > lb ? [la, lb] : [lb, la]
  return (high + 0.05) / (low + 0.05)
}

/** 模拟「颜色 10% 叠加到白色」后的实际底色 */
function mix(color, alpha) {
  const parse = (hex) => {
    const clean = hex.replace('#', '')
    return [0, 2, 4].map((index) => parseInt(clean.slice(index, index + 2), 16))
  }
  const fg = parse(color)
  const bg = parse('#FFFFFF')
  const toHex = (value) => Math.round(value).toString(16).padStart(2, '0')
  return `#${fg.map((value, index) => toHex(value * alpha + bg[index] * (1 - alpha))).join('')}`
}

let failed = 0
for (const [label, fg, bg, minimum] of PAIRS) {
  const value = ratio(fg, bg)
  const pass = value >= minimum
  if (!pass) {
    failed += 1
  }
  console.log(
    `${pass ? 'PASS' : 'FAIL'}  ${value.toFixed(2)}:1  (≥${minimum})  ${label}  ${fg} on ${bg}`,
  )
}

console.log(`\n合计 ${PAIRS.length} 组，未达标 ${failed} 组`)
process.exitCode = failed === 0 ? 0 : 1
