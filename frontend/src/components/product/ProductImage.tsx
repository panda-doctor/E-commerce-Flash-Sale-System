import { useState } from 'react'
import { ImageOff } from 'lucide-react'
import { cn } from '../../lib/cn'
import { toSameOriginPath } from '../../lib/asset'

/**
 * 商品图：`/uploads/**` 由网关代理到 product 应用；加载失败退化为占位块。
 * 后端可能返回内网绝对地址，这里统一归一化为同源路径（见 lib/asset.ts）。
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
    <div className={cn('relative overflow-hidden bg-page-subtle', ratio, className)}>
      {showFallback ? (
        <div className="flex size-full flex-col items-center justify-center gap-1.5 text-ink-400">
          <ImageOff className="size-7" aria-hidden />
          <span className="text-xs">暂无图片</span>
        </div>
      ) : (
        <img
          src={resolved}
          alt={alt}
          loading="lazy"
          decoding="async"
          onError={() => setFailed(true)}
          className="size-full object-cover transition-transform duration-200 hover:scale-[1.02]"
        />
      )}
    </div>
  )
}
