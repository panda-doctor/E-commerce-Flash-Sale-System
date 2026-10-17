/** 路由级懒加载的骨架屏（避免白屏闪烁）。 */
export function PageSkeleton() {
  return (
    <div className="min-h-dvh bg-page pt-16" aria-busy="true" aria-label="页面加载中">
      <div className="container-page space-y-6 py-10">
        <div className="h-8 w-48 animate-pulse rounded-md bg-state-line" />
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, index) => (
            <div key={index} className="card overflow-hidden">
              <div className="aspect-[4/3] w-full animate-pulse bg-state-line" />
              <div className="space-y-3 p-4">
                <div className="h-4 w-3/4 animate-pulse rounded bg-state-line" />
                <div className="h-4 w-1/2 animate-pulse rounded bg-state-line" />
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
