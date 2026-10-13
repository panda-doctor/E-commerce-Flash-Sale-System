import {
  ElAlert, ElAside, ElAvatar, ElButton, ElContainer, ElDatePicker, ElDialog,
  ElDivider, ElEmpty, ElForm, ElFormItem, ElHeader, ElInput, ElInputNumber,
  ElLoading, ElMain, ElOption, ElPagination, ElSelect, ElTable,
  ElTableColumn, ElTag, ElTooltip,
} from 'element-plus'
import type { Component } from 'vue'
import { createApp } from 'vue'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/main.css'
import './styles/storefront.css'
import './styles/storefront-refresh.css'

const app = createApp(App)
const components: Component[] = [
  ElAlert, ElAside, ElAvatar, ElButton, ElContainer, ElDatePicker, ElDialog,
  ElDivider, ElEmpty, ElForm, ElFormItem, ElHeader, ElInput, ElInputNumber,
  ElMain, ElOption, ElPagination, ElSelect, ElTable, ElTableColumn, ElTag, ElTooltip,
]
components.forEach((component) => {
  if (component.name) app.component(component.name, component)
})
app.use(ElLoading)
app.use(router)

// F2 修复：等待首次路由解析完成后再挂载。
// App.vue 依据 route.meta.surface 在「商城布局 / 后台壳层」之间分流，
// 而首帧渲染时 route.meta 仍为空（路由尚未 resolve），会先画出后台壳层再切换（可见闪屏）。
// 先 await isReady() 可保证首次渲染即命中正确布局。
void router.isReady().then(() => app.mount('#app'))

