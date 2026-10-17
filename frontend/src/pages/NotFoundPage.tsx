import { Link } from 'react-router-dom'
import { Compass } from 'lucide-react'

export default function NotFoundPage() {
  return (
    <div className="container-page flex min-h-[60vh] flex-col items-center justify-center gap-4 py-16 text-center">
      <Compass className="size-10 text-ink-400" aria-hidden />
      <h1 className="text-heading text-ink-900">页面走丢了</h1>
      <p className="text-body text-ink-600">你访问的地址不存在，或者商品已经下架。</p>
      <Link
        to="/"
        className="rounded-md bg-brand px-5 py-2.5 text-sm font-semibold text-white transition-colors duration-150 hover:bg-brand-dark focus-ring"
      >
        回到首页
      </Link>
    </div>
  )
}
