import { http } from './http'

// 所有后端接口集中定义，路径与后端 Controller 一一对应
export const api = {
  // 健康检查
  health: () => http.get('/api/health'),

  // 用户鉴权：动态发令牌（演示环境无账号系统，为自定义 userId 领取服务端随机令牌；
  // userId 字符串透传，后端 Long 接收；已占用 id 返回业务码 40903）
  registerUser: (userId) => http.post('/api/auth/register', { userId }),

  // 商品
  getProduct: (productId) => http.get(`/api/products/${productId}`),

  // 活动（用户端）
  listActivities: () => http.get('/api/seckill/activities'),
  getActivity: (activityId) => http.get(`/api/seckill/activities/${activityId}`),
  checkActivity: (activityId, userId) =>
    http.get(`/api/seckill/activities/${activityId}/check`, { userId }),

  // 秒杀执行（后端返回 { activityId, userId, result:'QUEUED', orderNo }）
  execute: (activityId, userId) =>
    http.post('/api/seckill/execute', { activityId, userId }),

  // 订单状态（QUEUING / CREATED）
  getOrder: (orderNo) => http.get(`/api/seckill/orders/${orderNo}`),
  // 我的秒杀订单（interface 4.10；activityId 可选）
  listMyOrders: (userId, activityId) =>
    http.get(`/api/seckill/users/${userId}/orders`, { activityId }),

  // 排行榜
  getRank: (activityId, top = 10) => http.get('/api/rank/top10', { activityId, top }),

  // 管理端：活动
  createActivity: (body) => http.post('/api/admin/seckill/activities', body),
  preheat: (activityId) => http.post(`/api/admin/seckill/activities/${activityId}/preheat`),

  // 管理端：运行指标 / 快照
  getMetrics: (activityId) =>
    http.get(`/api/admin/seckill/activities/${activityId}/metrics`),
  snapshot: (activityId) =>
    http.post(`/api/admin/seckill/activities/${activityId}/snapshot`),

  // 管理端：文件上传与商品保存
  uploadImage: (file) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.postForm('/api/admin/files/image', fd)
  },
  saveProduct: (body) => http.post('/api/admin/products', body),

  // AI 客服（请求 { message, history } → 返回 data.reply）
  aiChat: (body) => http.post('/api/support/chat', body),
}
