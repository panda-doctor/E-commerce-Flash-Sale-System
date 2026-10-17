import { Suspense } from 'react'
import { useRoutes } from 'react-router-dom'
import { routes } from './router'
import { PageSkeleton } from './components/ui/PageSkeleton'
import { Toaster } from './components/ui/Toast'

export default function App() {
  const element = useRoutes(routes)

  return (
    <>
      <Suspense fallback={<PageSkeleton />}>{element}</Suspense>
      <Toaster />
    </>
  )
}
