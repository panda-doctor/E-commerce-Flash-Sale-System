import type { Id } from './api'

/** `UserProfileResponse`：id/accountName/displayName/status/createdAt。 */
export interface UserProfile {
  id: Id
  accountName: string
  displayName: string | null
  /** 1 = 正常；其它值后端未在文档中细化，前端只做展示。 */
  status: number
  createdAt: string
}

/** `AuthResponse`：注意字段名是 `user`（不是 profile）。 */
export interface AuthSession {
  user: UserProfile
  csrfToken: string
}

export interface LoginPayload {
  accountName: string
  password: string
}

export interface RegisterPayload {
  accountName: string
  password: string
  displayName?: string
}
