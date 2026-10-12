import { http } from './http.ts'
import type {
  ActivityDetail,
  ActivityItem,
  ActivityMetrics,
  ActivityPayload,
  CreatedActivity,
  DeadLetterReplayResult,
  PreheatResult,
  ProductDetail,
  ProductPayload,
  SavedProduct,
  StockResetResult,
  UploadResult,
  AuthResponse,
  UserProfile,
  UserPage,
  SeckillOrderSummary,
  SeckillOrderStatus,
} from '../types/api'

export interface AiChatTurn {
  role: 'user' | 'assistant'
  content: string
}

export const api = {
  health: () => http.get<Record<string, string>>('/api/health'),
  listActivities: () => http.get<ActivityItem[]>('/api/seckill/activities'),
  getActivity: (activityId: string) =>
    http.get<ActivityDetail>(`/api/seckill/activities/${encodeURIComponent(activityId)}`),
  getProduct: (productId: string) =>
    http.get<ProductDetail>(`/api/products/${encodeURIComponent(productId)}`),
  getAdminProduct: (productId: string) =>
    http.get<ProductDetail>(`/api/admin/products/${encodeURIComponent(productId)}`),
  listProducts: (params: { page?: number; pageSize?: number; keyword?: string } = {}) =>
    http.get<UserPage<ProductDetail>>('/api/products', params),
  saveProduct: (payload: ProductPayload) =>
    http.post<SavedProduct>('/api/admin/products', payload),
  uploadImage: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return http.postForm<UploadResult>('/api/admin/files/image', form)
  },
  saveActivity: (payload: ActivityPayload) =>
    http.post<CreatedActivity>('/api/admin/seckill/activities', payload),
  preheat: (activityId: string) =>
    http.post<PreheatResult>(`/api/admin/seckill/activities/${encodeURIComponent(activityId)}/preheat`),
  getMetrics: (activityId: string) =>
    http.get<ActivityMetrics>(`/api/admin/seckill/activities/${encodeURIComponent(activityId)}/metrics`),
  snapshot: (activityId: string) =>
    http.post<void>(`/api/admin/seckill/activities/${encodeURIComponent(activityId)}/snapshot`),
  resetStock: (activityId: string) =>
    http.post<StockResetResult>(`/api/admin/seckill/activities/${encodeURIComponent(activityId)}/reset-stock`),
  replayDeadLetter: (originalMessageId: string) =>
    http.post<DeadLetterReplayResult>('/api/admin/seckill/dead-letters/replay', { originalMessageId }),
  register: (payload: { accountName: string; password: string; displayName?: string }) =>
    http.post<AuthResponse>('/api/auth/register', payload),
  login: (payload: { accountName: string; password: string }) =>
    http.post<AuthResponse>('/api/auth/login', payload),
  me: () => http.get<UserProfile>('/api/auth/me'),
  csrf: () => http.get<string>('/api/auth/csrf'),
  logout: () => http.post<void>('/api/auth/logout'),
  checkActivity: (activityId: string) =>
    http.get<{ activityId: string; userId: string; canJoin: boolean; activityStatus: string; reason: string }>(
      `/api/seckill/activities/${encodeURIComponent(activityId)}/check`,
    ),
  executeSeckill: (activityId: string) =>
    http.post<{ activityId: string; userId: string; result: string; orderNo?: string }>(
      '/api/seckill/execute', { activityId },
    ),
  listMyOrders: (activityId?: string) =>
    http.get<SeckillOrderSummary[]>('/api/seckill/users/me/orders', activityId ? { activityId } : {}),
  getOrderStatus: (orderNo: string) =>
    http.get<SeckillOrderStatus>(`/api/seckill/orders/${encodeURIComponent(orderNo)}`),
  aiChat: (payload: { message: string; history?: AiChatTurn[] }) =>
    http.post<{ reply: string }>('/api/support/chat', payload),
  listUsers: (params: { page: number; pageSize: number; keyword?: string; status?: number }) =>
    http.get<UserPage>('/api/admin/users', params),
  getUser: (userId: string) => http.get<UserProfile>(`/api/admin/users/${encodeURIComponent(userId)}`),
  createUser: (payload: { accountName: string; password: string; displayName?: string }) =>
    http.post<UserProfile>('/api/admin/users', payload),
  updateUser: (userId: string, payload: { displayName?: string; status?: number; password?: string }) =>
    http.put<UserProfile>(`/api/admin/users/${encodeURIComponent(userId)}`, payload),
  deleteUser: (userId: string) =>
    http.delete<void>(`/api/admin/users/${encodeURIComponent(userId)}`),
}
