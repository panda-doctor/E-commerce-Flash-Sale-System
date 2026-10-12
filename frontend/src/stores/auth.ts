import { reactive } from 'vue'
import { api } from '../api/apiClient'
import { setCsrfToken } from '../api/http.ts'
import type { UserProfile } from '../types/api'

export const authState = reactive({
  user: null as UserProfile | null,
  ready: false,
  loading: false,
})

let bootstrapPromise: Promise<void> | null = null

export async function bootstrapSession(): Promise<void> {
  if (authState.ready) return
  if (!bootstrapPromise) {
    bootstrapPromise = (async () => {
      authState.loading = true
      try {
        authState.user = await api.me()
        setCsrfToken(await api.csrf())
      } catch {
        authState.user = null
        setCsrfToken(null)
      } finally {
        authState.loading = false
        authState.ready = true
        bootstrapPromise = null
      }
    })()
  }
  await bootstrapPromise
}

export function establishSession(user: UserProfile, csrfToken: string): void {
  authState.user = user
  authState.ready = true
  setCsrfToken(csrfToken)
}

export function clearSession(): void {
  authState.user = null
  setCsrfToken(null)
}
