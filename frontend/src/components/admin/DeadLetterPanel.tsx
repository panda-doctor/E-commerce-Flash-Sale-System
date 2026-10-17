import { useState } from 'react'
import { RotateCcw } from 'lucide-react'
import { replayDeadLetters } from '../../api/admin'
import { useAdminToken } from '../../stores/adminToken'
import { Button } from '../ui/Button'
import { Card } from '../ui/Card'
import { toast } from '../ui/Toast'
import { toMessage } from '../../lib/errors'

/** 死信重放：按 originalMessageId 列表批量重投（单条失败不影响其余）。 */
export function DeadLetterPanel() {
  const { token } = useAdminToken()
  const [raw, setRaw] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [summary, setSummary] = useState<Record<string, unknown> | null>(null)

  const ids = raw
    .split(/[\s,;]+/)
    .map((item) => item.trim())
    .filter(Boolean)

  const onReplay = () => {
    if (ids.length === 0) {
      toast.error('请先粘贴至少一个 originalMessageId')
      return
    }
    setSubmitting(true)
    replayDeadLetters(token, ids)
      .then((result) => {
        setSummary(result)
        toast.success('批量重放已提交')
      })
      .catch((error: unknown) => {
        toast.error(toMessage(error))
        console.error('死信重放失败', error)
      })
      .finally(() => setSubmitting(false))
  }

  return (
    <Card
      title="死信重放"
      extra={
        <Button
          size="sm"
          loading={submitting}
          disabled={ids.length === 0}
          icon={<RotateCcw className="size-4" aria-hidden />}
          onClick={onReplay}
        >
          重放 {ids.length ? `(${ids.length})` : ''}
        </Button>
      }
    >
      <p className="mb-3 text-sm leading-6 text-ink-600">
        粘贴死信消息的 <code className="rounded bg-page px-1 text-xs">originalMessageId</code>
        （每行一个或逗号分隔）。后端会逐条重投，单条失败不影响其余，结果如下。
      </p>
      <textarea
        value={raw}
        onChange={(event) => setRaw(event.target.value)}
        rows={4}
        aria-label="死信消息 ID 列表"
        placeholder={'a1b2c3d4e5\nf6g7h8i9j0'}
        className="w-full rounded-md border border-state-line bg-page-card p-3 text-sm text-ink-900 outline-none transition-colors duration-150 placeholder:text-ink-400 focus:border-brand"
      />
      {summary ? (
        <pre className="mt-3 overflow-x-auto rounded-md bg-page p-3 text-xs text-ink-600">
          {JSON.stringify(summary, null, 2)}
        </pre>
      ) : null}
    </Card>
  )
}
