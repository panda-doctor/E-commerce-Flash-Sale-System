import { cn } from '../../lib/cn'

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-md bg-state-line', className)} aria-hidden />
}

export function ProductCardSkeleton() {
  return (
    <div className="card overflow-hidden">
      <Skeleton className="aspect-square w-full rounded-none" />
      <div className="space-y-2.5 p-3 sm:p-4">
        <Skeleton className="h-4 w-4/5" />
        <Skeleton className="h-4 w-1/2" />
        <Skeleton className="h-8 w-full rounded-full" />
      </div>
    </div>
  )
}
