import { useState } from 'react'
import type { ReactNode } from 'react'
import { KeyRound, ShieldCheck, X } from 'lucide-react'
import { AdminTokenProvider, useAdminToken } from '../../stores/adminToken'
import { Button } from '../ui/Button'
import { Input } from '../ui/Input'

function TokenGate({ children }: { children: ReactNode }) {
  const { token, setToken, clear } = useAdminToken()
  const [draft, setDraft] = useState('')

  if (token) {
    return (
      <div className="space-y-5">
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-state-line bg-white px-4 py-2.5 text-sm">
          <span className="flex items-center gap-2 text-ink-600">
            <ShieldCheck className="size-4 text-state-success" aria-hidden />
            已载入管理令牌（仅本标签页有效）
          </span>
          <Button
            size="sm"
            variant="ghost"
            icon={<X className="size-4" aria-hidden />}
            onClick={clear}
          >
            清除令牌
          </Button>
        </div>
        {children}
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-md rounded-xl border border-state-line bg-white p-6 shadow-card">
      <div className="flex items-center gap-2">
        <KeyRound className="size-5 text-brand" aria-hidden />
        <h1 className="text-lg font-bold text-ink-900">需要管理令牌</h1>
      </div>
      <p className="mt-2 text-sm leading-6 text-ink-600">
        管理接口要求 <code className="rounded bg-page px-1 py-0.5 text-xs">X-Admin-Token</code> 请求头。
        令牌由后端 <code className="rounded bg-page px-1 py-0.5 text-xs">ADMIN_TOKEN</code> 配置提供，
        只保存在本标签页的 sessionStorage 中。
      </p>
      <form
        className="mt-4 space-y-3"
        onSubmit={(event) => {
          event.preventDefault()
          setToken(draft)
          setDraft('')
        }}
      >
        <Input
          label="管理令牌"
          name="adminToken"
          type="password"
          placeholder="粘贴后端配置的管理令牌"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
        />
        <Button type="submit" block disabled={!draft.trim()}>
          载入令牌
        </Button>
      </form>
    </div>
  )
}

/** 管理端外壳：令牌闸门 + 内容区。 */
export function AdminShell({ children }: { children: ReactNode }) {
  return (
    <AdminTokenProvider>
      <TokenGate>{children}</TokenGate>
    </AdminTokenProvider>
  )
}
