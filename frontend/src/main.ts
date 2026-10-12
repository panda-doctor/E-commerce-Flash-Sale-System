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
app.mount('#app')

