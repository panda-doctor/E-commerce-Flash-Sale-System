import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/ActivityListView.vue'),
    meta: { title: '秒杀活动广场' },
  },
  {
    path: '/activity/:id(\\d+)',
    name: 'activity-detail',
    component: () => import('../views/ActivityDetailView.vue'),
    meta: { title: '商品秒杀' },
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('../views/AdminView.vue'),
    meta: { title: '管理控制台' },
  },
  {
    path: '/ai-service',
    name: 'ai-service',
    component: () => import('../views/AiServiceView.vue'),
    meta: { title: 'AI 购物助手' },
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.afterEach((to) => {
  document.title = `${to.meta.title ?? '看板'} · 闪电秒杀`
})

export default router
