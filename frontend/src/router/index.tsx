import { lazy } from 'react'
import type { RouteObject } from 'react-router-dom'
import { AppShell } from '../components/layout/AppShell'
import { RequireAuth } from '../components/auth/RequireAuth'

const ProductListPage = lazy(() => import('../pages/ProductListPage'))
const ProductDetailPage = lazy(() => import('../pages/ProductDetailPage'))
const ActivityListPage = lazy(() => import('../pages/ActivityListPage'))
const ActivityDetailPage = lazy(() => import('../pages/ActivityDetailPage'))
const OrdersPage = lazy(() => import('../pages/OrdersPage'))
const OrderDetailPage = lazy(() => import('../pages/OrderDetailPage'))
const AdminDashboardPage = lazy(() => import('../pages/admin/AdminDashboardPage'))
const AdminProductPage = lazy(() => import('../pages/admin/AdminProductPage'))
const AdminActivityPage = lazy(() => import('../pages/admin/AdminActivityPage'))
const LoginPage = lazy(() => import('../pages/LoginPage'))
const RegisterPage = lazy(() => import('../pages/RegisterPage'))
const ProfilePage = lazy(() => import('../pages/ProfilePage'))
const NotFoundPage = lazy(() => import('../pages/NotFoundPage'))

/**
 * 路由表：全部经 AppShell（固定导航 + 页脚）。
 * 页面级 lazy + App 里的 Suspense 骨架 = 按路由分包。
 * `RequireAuth` 作为父路由，未登录时跳登录并带回跳地址。
 */
export const routes: RouteObject[] = [
  {
    element: <AppShell />,
    children: [
      { index: true, element: <ProductListPage /> },
      { path: 'products', element: <ProductListPage /> },
      { path: 'products/:productId', element: <ProductDetailPage /> },
      { path: 'activities', element: <ActivityListPage /> },
      { path: 'activities/:activityId', element: <ActivityDetailPage /> },
      { path: 'admin', element: <AdminDashboardPage /> },
      { path: 'admin/products', element: <AdminProductPage /> },
      { path: 'admin/activities', element: <AdminActivityPage /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'register', element: <RegisterPage /> },
      {
        element: <RequireAuth />,
        children: [
          { path: 'profile', element: <ProfilePage /> },
          { path: 'orders', element: <OrdersPage /> },
          { path: 'orders/:orderNo', element: <OrderDetailPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
