import { reactive } from 'vue'

// 极简 toast：页面任意位置调用 push 提示
export const toasts = reactive([])
let seed = 0

export function toast(message, type = 'info', duration = 3200) {
  const id = ++seed
  toasts.push({ id, message, type })
  setTimeout(() => {
    const i = toasts.findIndex((t) => t.id === id)
    if (i >= 0) toasts.splice(i, 1)
  }, duration)
}

export const toastOK = (msg) => toast(msg, 'success')
export const toastWarn = (msg) => toast(msg, 'warning', 4500)
export const toastErr = (msg) => toast(msg, 'error', 6000)
