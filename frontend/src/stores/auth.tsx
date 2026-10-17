import { createContext, useCallback, useContext, useEffect, useMemo } from 'react'
import type { ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchCsrf, fetchMe, login as loginApi, logout as logoutApi, register as registerApi } from '../api/auth'
import { setCsrfToken, onUnauthorized } from '../lib/http'
import { clearUserScopedCache, queryKeys } from '../lib/queryKeys'
import type { LoginPayload, RegisterPayload, UserProfile } from '../types/auth'

interface AuthContextValue {
  user: UserProfile | null
  /** 首次恢复登录态（GET /api/auth/me）是否仍在进行：用于避免闪烁与误跳登录。 */
  restoring: boolean
  login: (payload: LoginPayload) => Promise<UserProfile>
  register: (payload: RegisterPayload) => Promise<UserProfile>
  /** 返回值表示服务端是否确认注销（false = 仅清理了本地状态，会话可能仍有效）。 */
  logout: () => Promise<boolean>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()

  // 刷新页面后靠 Cookie 会话恢复登录态；40100 视为未登录（不重试、不报错）
  const { data, isFetched } = useQuery({
    queryKey: queryKeys.authMe(),
    queryFn: fetchMe,
    retry: false,
    staleTime: 5 * 60_000,
    refetchOnWindowFocus: false,
  })

  // 首次 me 请求结束前不做"未登录"判定，避免受保护页面闪一下登录页
  const restoring = !isFetched
  const user = data ?? null

  const login = useCallback(
    async (payload: LoginPayload) => {
      const session = await loginApi(payload)
      // 换账号前先清掉上一个会话的订单缓存，避免新用户首帧看到他人数据
      clearUserScopedCache(queryClient)
      setCsrfToken(session.csrfToken)
      queryClient.setQueryData(queryKeys.authMe(), session.user)
      return session.user
    },
    [queryClient],
  )

  const register = useCallback(
    async (payload: RegisterPayload) => {
      const session = await registerApi(payload)
      clearUserScopedCache(queryClient)
      setCsrfToken(session.csrfToken)
      queryClient.setQueryData(queryKeys.authMe(), session.user)
      return session.user
    },
    [queryClient],
  )

  const logout = useCallback(async () => {
    // 注销失败也要清本地状态（否则界面还显示已登录），但要把"服务端未确认"如实返回给调用方
    let serverConfirmed = true
    await logoutApi().catch(() => {
      serverConfirmed = false
    })
    setCsrfToken(null)
    queryClient.setQueryData(queryKeys.authMe(), null)
    // 用户域缓存（订单列表 / 订单详情）必须整体清除：键里虽有 userId 兜底，但缓存不应留着
    clearUserScopedCache(queryClient)
    return serverConfirmed
  }, [queryClient])

  // 任意接口返回 40100：清空本地用户态与用户域缓存（路由守卫会据此跳登录）
  useEffect(() => {
    onUnauthorized(() => {
      setCsrfToken(null)
      queryClient.setQueryData(queryKeys.authMe(), null)
      clearUserScopedCache(queryClient)
    })
    return () => onUnauthorized(null)
  }, [queryClient])

  // 会话续期会让 CSRF 轮换：恢复登录态后主动取一次，保证首次写操作不会 40301
  useEffect(() => {
    if (user) {
      fetchCsrf()
        .then(setCsrfToken)
        .catch((error: unknown) => console.error('获取 CSRF token 失败', error))
    }
  }, [user])

  const value = useMemo<AuthContextValue>(
    () => ({ user, restoring, login, register, logout }),
    [user, restoring, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth 必须在 <AuthProvider> 内使用')
  }
  return context
}
