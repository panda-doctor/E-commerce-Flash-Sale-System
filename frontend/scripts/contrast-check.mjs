/**
 * 配色对比度自检（WCAG 2.1），验证暖色品牌令牌的前景/背景组合。
 * 纯计算，不依赖浏览器。用法：node scripts/contrast-check.mjs
 *
 * 阈值约定：
 * - 承载正文的文字 ≥4.5:1；
 * - 大字号（≥24px 或 ≥18.66px 粗体）促销价格 ≥3:1，单独标注；
 * - 弱化文字 #8A8490 仅用于占位符/图标/禁用态，不承载信息，阈值 0；
 * - 橙色/珊瑚色只做装饰与渐变，不与白字组合，阈值 0。
 */

const TOKENS = {
  pageBg: '#FFF7F0',
  surface: '#FFFFFF',
  surfaceMuted: '#FFF0E5',
  brandSoft: '#FFE7E5',
  textPrimary: '#24202A',
  textSecondary: '#6B6470',
  textMuted: '#8A8490',
  border: '#F0E1DA',
  brand: '#E53935',
  brandSolid: '#D32F2F',
  brandDark: '#C62828',
  brandOrange: '#FF8A34',
  footerBg: '#2A1B19',
  footerText: '#F7E9E4',
  footerMuted: '#C9B4AC',
  success: '#12724E',
  warning: '#9A5B08',
  danger: '#C42A1F',
}

const PAIRS = [
  ['主文字 / 页面底', TOKENS.textPrimary, TOKENS.pageBg, 4.5],
  ['主文字 / 白卡', TOKENS.textPrimary, TOKENS.surface, 4.5],
  ['次级文字 / 白卡', TOKENS.textSecondary, TOKENS.surface, 4.5],
  ['次级文字 / 页面底', TOKENS.textSecondary, TOKENS.pageBg, 4.5],
  ['次级文字 / 浅橙底', TOKENS.textSecondary, TOKENS.surfaceMuted, 4.5],
  ['次级文字 / 浅红底', TOKENS.textSecondary, TOKENS.brandSoft, 4.5],
  ['弱化文字 / 白卡（仅占位/图标）', TOKENS.textMuted, TOKENS.surface, 0],
  ['白字 / 品牌实心按钮', TOKENS.surface, TOKENS.brandSolid, 4.5],
  ['白字 / 品牌按钮 hover', TOKENS.surface, TOKENS.brandDark, 4.5],
  ['浅红底小字 / 浅红底', TOKENS.brandDark, TOKENS.brandSoft, 4.5],
  ['深红字 / 浅橙底', TOKENS.brandDark, TOKENS.surfaceMuted, 4.5],
  ['秒杀价大字 / 白卡（大字阈值）', TOKENS.brand, TOKENS.surface, 3],
  ['秒杀价大字 / 浅红底（大字阈值）', TOKENS.brand, TOKENS.brandSoft, 3],
  ['白字 / 橙色（仅装饰，不承载文字）', TOKENS.surface, TOKENS.brandOrange, 0],
  ['页脚正文 / 深暖底', TOKENS.footerText, TOKENS.footerBg, 4.5],
  ['页脚次要 / 深暖底', TOKENS.footerMuted, TOKENS.footerBg, 4.5],
  ['成功 / 白卡', TOKENS.success, TOKENS.surface, 4.5],
  ['警告 / 白卡', TOKENS.warning, TOKENS.surface, 4.5],
  ['危险 / 白卡', TOKENS.danger, TOKENS.surface, 4.5],
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
