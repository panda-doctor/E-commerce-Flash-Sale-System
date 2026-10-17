import { createContext, useCallback, useContext, useEffect, useMemo } from 'react'
import type { ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchCsrf, fetchMe, login as loginApi, logout as logoutApi, register as registerApi } from '../api/auth'
import { setCsrfToken, onUnauthorized } from '../lib/http'
import type { LoginPayload, RegisterPayload, UserProfile } from '../types/auth'

interface AuthContextValue {
  user: UserProfile | null
  /** 首次恢复登录态（GET /api/auth/me）是否仍在进行：用于避免闪烁与误跳登录。 */
  restoring: boolean
  login: (payload: LoginPayload) => Promise<UserProfile>
  register: (payload: RegisterPayload) => Promise<UserProfile>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()

  // 刷新页面后靠 Cookie 会话恢复登录态；40100 视为未登录（不重试、不报错）
  const { data, isFetched } = useQuery({
    queryKey: ['auth', 'me'],
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
      setCsrfToken(session.csrfToken)
      queryClient.setQueryData(['auth', 'me'], session.user)
      return session.user
    },
    [queryClient],
  )

  const register = useCallback(
    async (payload: RegisterPayload) => {
      const session = await registerApi(payload)
      setCsrfToken(session.csrfToken)
      queryClient.setQueryData(['auth', 'me'], session.user)
      return session.user
    },
    [queryClient],
  )

  const logout = useCallback(async () => {
    await logoutApi().catch(() => undefined)
    setCsrfToken(null)
    queryClient.setQueryData(['auth', 'me'], null)
    queryClient.removeQueries({ queryKey: ['orders'] })
  }, [queryClient])

  // 任意接口返回 40100：清空本地用户态（路由守卫会据此跳登录）
  useEffect(() => {
    onUnauthorized(() => {
      setCsrfToken(null)
      queryClient.setQueryData(['auth', 'me'], null)
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
