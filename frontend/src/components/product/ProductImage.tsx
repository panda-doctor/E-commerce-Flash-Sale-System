import { useState } from 'react'
import { ImageOff } from 'lucide-react'
import { cn } from '../../lib/cn'
import { toSameOriginPath } from '../../lib/asset'

/**
 * 商品图：`/uploads/**` 由网关代理到 product 应用；加载失败或**退化图片**退化为暖色占位块。
 *
 * 退化图片保护：后端联调数据里存在 1×1 的纯色 PNG（实测 68 字节、像素 rgba(255,0,0,255)），
 * 被 object-cover 拉伸后就是整块纯红。这里按"边长 < 8px 视为占位像素"处理，
 * 属于无效素材兜底，不是掩盖加载错误（真正的加载失败仍由 onError 处理）。
 */
export function ProductImage({
  src,
  alt,
  className,
  ratio = 'aspect-[4/3]',
}: {
  src: string | null | undefined
  alt: string
  className?: string
  ratio?: string
}) {
  const [failed, setFailed] = useState(false)
  const resolved = toSameOriginPath(src)
  const showFallback = !resolved || failed

  return (
    <div className={cn('relative overflow-hidden bg-image-warm', ratio, className)}>
      {showFallback ? (
        <div className="flex size-full flex-col items-center justify-center gap-2 text-ink-600">
          {/* 柔和光斑让占位区像一个"设计过的"空态，而不是加载失败的破图 */}
          <div
            aria-hidden
            className="pointer-events-none absolute -left-6 -top-8 size-28 rounded-full bg-white/60 blur-2xl"
          />
          <div
            aria-hidden
            className="pointer-events-none absolute -bottom-10 -right-6 size-32 rounded-full bg-brand-soft/80 blur-2xl"
          />
          <span className="relative flex size-12 items-center justify-center rounded-2xl border border-white/80 bg-white/75 shadow-sm sm:size-14">
            <ImageOff className="size-5 text-brand-orange sm:size-6" aria-hidden />
          </span>
          <span className="relative text-[11px] font-medium tracking-wide text-ink-600 sm:text-xs">
            暂无商品图
          </span>
        </div>
      ) : (
        <img
          src={resolved}
          alt={alt}
          loading="lazy"
          decoding="async"
          onError={() => setFailed(true)}
          onLoad={(event) => {
            const image = event.currentTarget
            if (image.naturalWidth < 8 || image.naturalHeight < 8) {
              setFailed(true)
            }
          }}
          className="size-full object-cover transition-transform duration-200 hover:scale-[1.03] motion-reduce:transform-none"
        />
      )}
    </div>
  )
}
