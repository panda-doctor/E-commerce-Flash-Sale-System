import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../../stores/auth'
import { PageSkeleton } from '../ui/PageSkeleton'

/** 受保护路由：会话恢复完成前显示骨架，未登录跳登录并带回跳地址。 */
export function RequireAuth() {
  const { user, restoring } = useAuth()
  const location = useLocation()

  if (restoring) {
    return <PageSkeleton />
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
  }

  return <Outlet />
}
