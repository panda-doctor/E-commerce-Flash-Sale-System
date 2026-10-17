import { Link } from 'react-router-dom'
import { PackageCheck, ShieldCheck, Timer } from 'lucide-react'

const QUICK_LINKS = [
  { to: '/', label: '首页' },
  { to: '/products', label: '全部商品' },
  { to: '/activities', label: '秒杀活动' },
  { to: '/orders', label: '我的订单' },
]

/** 页脚：品牌说明 + 快捷入口 + 真实能力说明，三层信息不抢主内容焦点。 */
export function Footer() {
  return (
    <footer className="mt-16 border-t border-state-line bg-page-card">
      <div className="container-page grid gap-8 py-10 sm:grid-cols-2 lg:grid-cols-[1.5fr_1fr_1fr]">
        <div>
          <Link to="/" className="inline-flex items-center gap-2 rounded-md focus-ring">
            <span className="flex size-8 items-center justify-center rounded-lg bg-brand text-sm font-bold text-white">
              闪
            </span>
            <span className="text-base font-bold text-ink-900">闪购秒杀</span>
          </Link>
          <p className="mt-3 max-w-sm text-sm leading-6 text-ink-600">
            限时好物，整点开抢。商品、库存、活动与订单数据均来自实时服务，不含演示数据。
          </p>
        </div>

        <nav aria-label="页脚导航">
          <h2 className="text-sm font-semibold text-ink-900">快速入口</h2>
          <ul className="mt-3 space-y-2">
            {QUICK_LINKS.map((item) => (
              <li key={item.to}>
                <Link
                  to={item.to}
                  className="rounded-sm text-sm text-ink-600 transition-colors duration-150 hover:text-ink-900 focus-ring"
                >
                  {item.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <div>
          <h2 className="text-sm font-semibold text-ink-900">购物保障</h2>
          <ul className="mt-3 space-y-2 text-sm text-ink-600">
            <li className="flex items-center gap-2">
              <ShieldCheck className="size-4 text-ink-400" aria-hidden />
              库存由服务端实时校验
            </li>
            <li className="flex items-center gap-2">
              <Timer className="size-4 text-ink-400" aria-hidden />
              倒计时以服务端时间为准
            </li>
            <li className="flex items-center gap-2">
              <PackageCheck className="size-4 text-ink-400" aria-hidden />
              一人一件，排队结果可查
            </li>
          </ul>
        </div>
      </div>

      <div className="border-t border-state-line">
        <div className="container-page flex flex-col gap-2 py-4 text-xs text-ink-600 sm:flex-row sm:items-center sm:justify-between">
          <span>© 2026 闪购秒杀 · 联调演示环境</span>
          <span className="tnum">接口数据实时读取，页面不缓存伪造结果</span>
        </div>
      </div>
    </footer>
  )
}
