import { create } from 'zustand'
import { CheckCircle2, Info, X, XCircle } from 'lucide-react'
import { cn } from '../../lib/cn'

type ToastTone = 'success' | 'error' | 'info'

interface ToastItem {
  id: number
  tone: ToastTone
  message: string
}

interface ToastStore {
  items: ToastItem[]
  push: (tone: ToastTone, message: string) => void
  remove: (id: number) => void
}

let seq = 0

/** 同屏最多展示的提示条数（超出则挤掉最旧的一条）。 */
const MAX_VISIBLE = 3

/** 极简 toast：不引入额外依赖，zustand 只为共享一份队列（体积 ~1KB）。 */
export const useToastStore = create<ToastStore>((set) => ({
  items: [],
  push: (tone, message) => {
    const id = ++seq
    // 只保留最近 3 条：连续失败时避免堆叠把页面遮住
    set((state) => ({ items: [...state.items, { id, tone, message }].slice(-MAX_VISIBLE) }))
    window.setTimeout(() => {
      set((state) => ({ items: state.items.filter((item) => item.id !== id) }))
    }, 4000)
  },
  remove: (id) => set((state) => ({ items: state.items.filter((item) => item.id !== id) })),
}))

export const toast = {
  success: (message: string) => useToastStore.getState().push('success', message),
  error: (message: string) => useToastStore.getState().push('error', message),
  info: (message: string) => useToastStore.getState().push('info', message),
}

const TONE_STYLE: Record<ToastTone, string> = {
  success: 'border-state-success/30 bg-white text-ink-900',
  error: 'border-state-danger/30 bg-white text-ink-900',
  info: 'border-state-info/30 bg-white text-ink-900',
}

const TONE_ICON: Record<ToastTone, typeof Info> = {
  success: CheckCircle2,
  error: XCircle,
  info: Info,
}

const TONE_COLOR: Record<ToastTone, string> = {
  success: 'text-state-success',
  error: 'text-state-danger',
  info: 'text-state-info',
}

export function Toaster() {
  const items = useToastStore((state) => state.items)
  const remove = useToastStore((state) => state.remove)

  if (items.length === 0) {
    return null
  }

  return (
    <div className="fixed bottom-6 right-4 z-toast flex w-[calc(100%-2rem)] max-w-sm flex-col gap-2">
      {items.map((item) => {
        const Icon = TONE_ICON[item.tone]
        return (
          <div
            key={item.id}
            role="status"
            className={cn(
              'flex items-start gap-3 rounded-lg border px-4 py-3 text-sm shadow-card animate-in slide-in-from-bottom-2',
              TONE_STYLE[item.tone],
            )}
          >
            <Icon className={cn('mt-0.5 size-4 shrink-0', TONE_COLOR[item.tone])} aria-hidden />
            <p className="flex-1 leading-6">{item.message}</p>
            <button
              type="button"
              onClick={() => remove(item.id)}
              aria-label="关闭提示"
              className="cursor-pointer rounded p-0.5 text-ink-400 transition-colors duration-150 hover:text-ink-900 focus-ring"
            >
              <X className="size-4" aria-hidden />
            </button>
          </div>
        )
      })}
    </div>
  )
}
