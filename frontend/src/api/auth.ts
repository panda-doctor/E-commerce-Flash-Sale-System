import { http } from '../lib/http'
import type { AuthSession, LoginPayload, RegisterPayload, UserProfile } from '../types/auth'

/** POST /api/auth/login → { user, csrfToken } */
export function login(payload: LoginPayload) {
  return http.post<AuthSession>('/api/auth/login', payload)
}

/** POST /api/auth/register → { user, csrfToken } */
export function register(payload: RegisterPayload) {
  return http.post<AuthSession>('/api/auth/register', payload)
}

/** GET /api/auth/me → 当前登录用户（未登录返回 40100）。 */
export function fetchMe() {
  return http.get<UserProfile>('/api/auth/me')
}

/** GET /api/auth/csrf → 新的 CSRF token（会话续期后轮换时使用）。 */
export function fetchCsrf() {
  return http.get<string>('/api/auth/csrf')
}

/** POST /api/auth/logout */
export function logout() {
  return http.post<null>('/api/auth/logout')
}
