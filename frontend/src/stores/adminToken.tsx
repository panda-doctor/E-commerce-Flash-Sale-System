import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

const STORAGE_KEY = 'flash.admin.token'

interface AdminTokenValue {
  token: string
  setToken: (token: string) => void
  clear: () => void
}

const AdminTokenContext = createContext<AdminTokenValue | null>(null)

/**
 * 管理令牌只存 sessionStorage（关闭标签页即失效）：
 * 不写入源码、不写 localStorage、不打日志。
 */
export function AdminTokenProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState(() => sessionStorage.getItem(STORAGE_KEY) ?? '')

  const setToken = useCallback((next: string) => {
    const trimmed = next.trim()
    setTokenState(trimmed)
    if (trimmed) {
      sessionStorage.setItem(STORAGE_KEY, trimmed)
    } else {
      sessionStorage.removeItem(STORAGE_KEY)
    }
  }, [])

  const clear = useCallback(() => {
    setTokenState('')
    sessionStorage.removeItem(STORAGE_KEY)
  }, [])

  const value = useMemo(() => ({ token, setToken, clear }), [token, setToken, clear])

  return <AdminTokenContext.Provider value={value}>{children}</AdminTokenContext.Provider>
}

export function useAdminToken() {
  const context = useContext(AdminTokenContext)
  if (!context) {
    throw new Error('useAdminToken 必须在 <AdminTokenProvider> 内使用')
  }
  return context
}
