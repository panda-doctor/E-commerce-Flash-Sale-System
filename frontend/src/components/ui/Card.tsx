import type { HTMLAttributes, ReactNode } from 'react'
import { cn } from '../../lib/cn'

interface CardProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  /** 卡片标题（标题区自带分隔线）。与 HTML 的 title 属性同名会冲突，因此单独定义。 */
  title?: ReactNode
  extra?: ReactNode
  padded?: boolean
}

export function Card({ title, extra, padded = true, className, children, ...rest }: CardProps) {
  return (
    <section className={cn('card', className)} {...rest}>
      {title || extra ? (
        <header className="flex items-center justify-between border-b border-state-line px-5 py-3.5">
          <h2 className="text-subheading text-ink-900">{title}</h2>
          {extra}
        </header>
      ) : null}
      <div className={cn(padded && 'p-5')}>{children}</div>
    </section>
  )
}
