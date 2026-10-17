import { Badge } from '../ui/Badge'
import type { OrderStatus } from '../../types/order'
import type { PhaseTone } from '../../lib/activityPhase'

const STATUS_META: Record<string, { text: string; tone: PhaseTone }> = {
  QUEUING: { text: '排队中', tone: 'info' },
  CREATED: { text: '已创建', tone: 'success' },
  CREATE_FAILED: { text: '创建失败', tone: 'danger' },
  CANCELLED: { text: '已取消', tone: 'neutral' },
}

export function OrderStatusBadge({ status }: { status: OrderStatus | undefined }) {
  const meta = status ? STATUS_META[status] : undefined
  return <Badge tone={meta?.tone ?? 'neutral'}>{meta?.text ?? status ?? '未知'}</Badge>
}

export function orderStatusText(status: OrderStatus | undefined): string {
  return status ? (STATUS_META[status]?.text ?? status) : '未知'
}
