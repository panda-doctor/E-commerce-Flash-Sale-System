import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/', component: () => import('../layouts/StorefrontLayout.vue'), meta: { surface: 'store' },
    children: [
      { path: '', name: 'shop-home', component: () => import('../views/ShopHomeView.vue'), meta: { title: '全部商品' } },
      { path: 'flash-sale', name: 'flash-sale', component: () => import('../views/ShopHomeView.vue'), meta: { title: '限时秒杀', saleOnly: true } },
      { path: 'products/:productId', name: 'product-detail', component: () => import('../views/ProductDetailView.vue'), meta: { title: '商品详情' } },
      { path: 'sign-in', name: 'sign-in', component: () => import('../views/SignInView.vue'), meta: { title: '登录' } },
      { path: 'login', redirect: (to) => ({ name: 'sign-in', query: to.query }) },
      { path: 'register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { title: '注册' } },
      { path: 'account/orders', name: 'my-orders', component: () => import('../views/MyOrdersView.vue'), meta: { title: '我的订单' } },
      { path: 'orders', redirect: { name: 'my-orders' } },
    ],
  },
  { path: '/dashboard', name: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '运营总览', section: '工作台' } },
  { path: '/products', name: 'products', component: () => import('../views/ProductsView.vue'), meta: { title: '商品管理', section: '商品与活动' } },
  { path: '/activities', name: 'activities', component: () => import('../views/ActivitiesView.vue'), meta: { title: '秒杀活动', section: '商品与活动' } },
  { path: '/users', name: 'users', component: () => import('../views/UsersView.vue'), meta: { title: '用户管理', section: '用户与权限' } },
  { path: '/operations', name: 'operations', component: () => import('../views/OperationsView.vue'), meta: { title: '运行监控', section: '系统运维' } },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({ history: createWebHistory(), routes })
router.afterEach((to) => { document.title = `${String(to.meta.title ?? '商城')} · 闪购集市` })
export default router
