import type { ReactNode } from 'react'
import { Flame, ShieldCheck, Timer } from 'lucide-react'

/** 登录/注册共用外壳：左侧暖色卖点面板，右侧白色表单。 */
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
    <div className="container-page py-10 sm:py-14">
      <div className="mx-auto grid max-w-4xl overflow-hidden rounded-3xl border border-state-line bg-page-card shadow-card md:grid-cols-[1fr_1.1fr]">
        <aside className="hidden flex-col justify-between bg-[linear-gradient(160deg,#FFF3EA_0%,#FFF1EC_45%,#FFE7E5_100%)] p-8 md:flex">
          <div className="flex items-center gap-2">
            <span className="flex size-9 items-center justify-center rounded-xl bg-gradient-to-br from-brand-orange to-brand text-sm font-bold text-white">
              闪
            </span>
            <span className="text-lg font-bold text-ink-900">闪购秒杀</span>
          </div>
          <div className="space-y-4">
            <h2 className="text-2xl font-bold leading-9 text-ink-900">
              抢到就是赚到
              <br />
              库存实时可见
            </h2>
            <ul className="space-y-2.5 text-sm text-ink-600">
              <li className="flex items-center gap-2">
                <span className="flex size-6 items-center justify-center rounded-full bg-white/80 text-brand-dark">
                  <Timer className="size-3.5" aria-hidden />
                </span>
                整点开抢，倒计时精确到秒
              </li>
              <li className="flex items-center gap-2">
                <span className="flex size-6 items-center justify-center rounded-full bg-white/80 text-brand-dark">
                  <Flame className="size-3.5" aria-hidden />
                </span>
                一人一件，杜绝脚本刷单
              </li>
              <li className="flex items-center gap-2">
                <span className="flex size-6 items-center justify-center rounded-full bg-white/80 text-brand-dark">
                  <ShieldCheck className="size-3.5" aria-hidden />
                </span>
                排队结果随时可查
              </li>
            </ul>
          </div>
          <p className="text-xs text-ink-600">数据来自实时服务，不含演示假数据。</p>
        </aside>

        <div className="p-6 sm:p-9">
          <h1 className="text-2xl font-extrabold text-ink-900">{title}</h1>
          <p className="mt-1.5 text-sm text-ink-600">{subtitle}</p>
          <div className="mt-6">{children}</div>
          {footer ? <div className="mt-5 text-sm text-ink-600">{footer}</div> : null}
        </div>
      </div>
    </div>
  )
}
