import { useState } from 'react'
import { Camera, Gauge } from 'lucide-react'
import { AdminShell } from '../../components/admin/AdminShell'
import { MetricsPanel } from '../../components/admin/MetricsPanel'
import { DeadLetterPanel } from '../../components/admin/DeadLetterPanel'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { toast } from '../../components/ui/Toast'
import { useAdminToken } from '../../stores/adminToken'
import { useActivities } from '../../hooks/queries/useActivities'
import { captureSnapshot } from '../../api/admin'
import { toMessage } from '../../lib/errors'
import { toId } from '../../lib/id'

/** 管理控制台：选择活动 → 实时指标 + 快照落库 + 死信重放。 */
export default function AdminDashboardPage() {
  const { token } = useAdminToken()
  const [activityId, setActivityId] = useState('')
  const [snapshotting, setSnapshotting] = useState(false)
  const activitiesQuery = useActivities()

  const onSnapshot = () => {
    if (!activityId.trim()) {
      toast.error('请先填写活动 ID')
      return
    }
    setSnapshotting(true)
    captureSnapshot(token, activityId.trim())
      .then(() => toast.success('指标快照已落库'))
      .catch((error: unknown) => {
        toast.error(toMessage(error))
        console.error('快照失败', error)
      })
      .finally(() => setSnapshotting(false))
  }

  return (
    <AdminShell>
      <div className="container-page space-y-5 py-8">
        <div className="flex flex-col gap-2">
          <h1 className="text-heading text-ink-900">管理控制台</h1>
          <p className="text-sm text-ink-600">
            指标与快照均调用真实管理接口；活动列表来自公开接口，仅用于选择活动 ID。
          </p>
        </div>

        <Card
          title={
            <span className="flex items-center gap-2">
              <Gauge className="size-4 text-ink-400" aria-hidden />
              选择活动
            </span>
          }
        >
          <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
            <div className="flex-1">
              <Input
                label="活动 ID"
                name="activityId"
                placeholder="例如 1788829276582000000"
                value={activityId}
                onChange={(event) => setActivityId(event.target.value)}
                hint="可直接粘贴，也可从下方列表选择"
              />
            </div>
            <Button
              variant="secondary"
              loading={snapshotting}
              icon={<Camera className="size-4" aria-hidden />}
              onClick={onSnapshot}
            >
              落库快照
            </Button>
          </div>

          {activitiesQuery.data && activitiesQuery.data.length > 0 ? (
            <div className="mt-4">
              <p className="mb-2 text-xs text-ink-600">快速选择</p>
              <div className="flex flex-wrap gap-2">
                {activitiesQuery.data.slice(0, 12).map((item) => (
                  <button
                    key={item.activityId}
                    type="button"
                    onClick={() => setActivityId(toId(item.activityId))}
                    className={
                      activityId === item.activityId
                        ? 'cursor-pointer rounded-full bg-ink-900 px-3 py-1 text-xs font-semibold text-white'
                        : 'cursor-pointer rounded-full border border-state-line bg-page-card px-3 py-1 text-xs text-ink-600 transition-colors duration-150 hover:border-state-line-strong hover:text-ink-900'
                    }
                  >
                    {item.activityName ?? item.activityId}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <p className="mt-3 text-xs text-ink-600">
              暂无可选活动（公开活动列表为空时可手动输入活动 ID）。
            </p>
          )}
        </Card>

        {activityId.trim() ? <MetricsPanel activityId={activityId.trim()} /> : null}
        <DeadLetterPanel />
      </div>
    </AdminShell>
  )
}
