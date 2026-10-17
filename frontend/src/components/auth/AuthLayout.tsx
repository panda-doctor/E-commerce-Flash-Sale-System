import type { ReactNode } from 'react'
import { Flame, ShieldCheck, Timer } from 'lucide-react'

/** 登录/注册共用的双栏外壳：左侧卖点，右侧表单。 */
export function AuthLayout({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string
  subtitle: string
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <div className="container-page py-12">
      <div className="mx-auto grid max-w-4xl overflow-hidden rounded-2xl border border-state-line bg-white shadow-card md:grid-cols-[1fr_1.1fr]">
        <aside className="hidden flex-col justify-between bg-ink-900 p-8 text-white md:flex">
          <div className="flex items-center gap-2">
            <span className="flex size-9 items-center justify-center rounded-lg bg-brand text-sm font-bold">闪</span>
            <span className="text-lg font-bold">闪购秒杀</span>
          </div>
          <div className="space-y-4">
            <h2 className="text-2xl font-bold leading-9 text-balance">
              抢到就是赚到，
              <br />
              库存实时可见
            </h2>
            <ul className="space-y-2.5 text-sm text-white/70">
              <li className="flex items-center gap-2">
                <Timer className="size-4 text-brand" aria-hidden />
                整点开抢，倒计时精确到秒
              </li>
              <li className="flex items-center gap-2">
                <Flame className="size-4 text-brand" aria-hidden />
                一人一件，杜绝脚本刷单
              </li>
              <li className="flex items-center gap-2">
                <ShieldCheck className="size-4 text-brand" aria-hidden />
                排队结果随时可查
              </li>
            </ul>
          </div>
          <p className="text-xs text-white/40">数据来自实时服务，不含演示假数据。</p>
        </aside>

        <div className="p-6 sm:p-9">
          <h1 className="text-2xl font-bold text-ink-900">{title}</h1>
          <p className="mt-1.5 text-sm text-ink-600">{subtitle}</p>
          <div className="mt-6">{children}</div>
          {footer ? <div className="mt-5 text-sm text-ink-600">{footer}</div> : null}
        </div>
      </div>
    </div>
  )
}
