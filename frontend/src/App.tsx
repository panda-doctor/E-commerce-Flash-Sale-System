import { Suspense } from 'react'
import { useRoutes } from 'react-router-dom'
import { routes } from './router'
import { PageSkeleton } from './components/ui/PageSkeleton'
import { Toaster } from './components/ui/Toast'
import { ErrorBoundary } from './components/system/ErrorBoundary'

export default function App() {
  const element = useRoutes(routes)

  return (
    <>
      {/* 路由级渲染异常由 ErrorBoundary 兜底；Toaster 放在边界外，保证异常时仍能展示提示 */}
      <ErrorBoundary>
        <Suspense fallback={<PageSkeleton />}>{element}</Suspense>
      </ErrorBoundary>
      <Toaster />
    </>
  )
}
