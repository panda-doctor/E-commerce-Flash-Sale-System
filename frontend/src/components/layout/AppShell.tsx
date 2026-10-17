import { Outlet, useLocation } from 'react-router-dom'
import { useEffect } from 'react'
import { TopNav } from './TopNav'
import { Footer } from './Footer'

/** 全站外壳：固定导航 + 内容区（pt-16 避免被遮挡）+ 页脚。 */
export function AppShell() {
  const { pathname } = useLocation()

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'instant' })
  }, [pathname])

  return (
    <div className="flex min-h-dvh flex-col bg-page">
      <TopNav />
      <main className="flex-1 pt-16">
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}
