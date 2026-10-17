import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import * as Dialog from '@radix-ui/react-dialog'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Loader2, RefreshCw, ShoppingBag, X } from 'lucide-react'
import { executeSeckill } from '../../api/seckill'
import { useAuth } from '../../stores/auth'
import { useOrderPolling } from '../../hooks/queries/useOrders'
import { queryKeys } from '../../lib/queryKeys'
import { Button } from '../ui/Button'
import { Badge } from '../ui/Badge'
import { toast } from '../ui/Toast'
import { SECKILL_RESULT_TEXT, toMessage } from '../../lib/errors'
import { PHASE_TEXT, phaseHint, type Phase } from '../../lib/activityPhase'
import type { SeckillExecuteResult } from '../../types/activity'

const STATUS_TEXT: Record<string, string> = {
  QUEUING: '排队中，正在生成订单…',
  CREATED: '排队成功，订单已生成',
  CREATE_FAILED: '很遗憾，订单创建失败',
  CANCELLED: '订单已取消',
}

/**
 * 秒杀交易面板：提交 → 按 `result` 枚举反馈 → QUEUED 时用订单号轮询至终态。
 * 后端不提供进度百分比，因此只展示"排队中/成功/失败"三个真实状态。
 */
export function SeckillPanel({
  activityId,
  productId,
  phase,
  limitPerUser,
}: {
  activityId: string
  productId: string | undefined
  phase: Phase
  limitPerUser: number | null
}) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [result, setResult] = useState<SeckillExecuteResult | null>(null)
  const [orderNo, setOrderNo] = useState<string | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const orderQuery = useOrderPolling(orderNo ?? undefined, Boolean(orderNo))
  const orderStatus = orderQuery.data?.status

  /** 已处理过的终态签名，保证同一订单的同一终态只触发一次缓存刷新。 */
  const handledRef = useRef<string | null>(null)

  const mutation = useMutation({
    mutationFn: () => executeSeckill(activityId, productId),
    onSuccess: (data) => {
      setResult(data)
      setDialogOpen(true)
      if (data.result === 'QUEUED' && data.orderNo) {
        setOrderNo(data.orderNo)
        toast.info('已进入抢购队列，正在为你生成订单')
      } else if (data.result !== 'QUEUED') {
        toast.error(SECKILL_RESULT_TEXT[data.result])
      }
      // 提交后库存可能已扣减：列表与详情一并刷新（榜单等终态后再刷）
      void queryClient.invalidateQueries({ queryKey: ['orders'] })
      void queryClient.invalidateQueries({ queryKey: queryKeys.activity(activityId) })
      void queryClient.invalidateQueries({ queryKey: queryKeys.activities() })
    },
    onError: (error: unknown) => {
      const message = toMessage(error)
      setErrorMessage(message)
      setDialogOpen(true)
      console.error('秒杀提交失败', error)
    },
  })

  /**
   * 轮询到终态后刷新相关缓存。
   * 必须放在 effect 里：渲染阶段执行 queryClient 副作用会让每次渲染都触发一次失效，
   * 数据变化引起的重渲染又再次触发，形成刷新循环（本处曾写在渲染体，已修正）。
   */
  useEffect(() => {
    if (!orderNo || !orderStatus || orderStatus === 'QUEUING') {
      return
    }
    const signature = `${orderNo}:${orderStatus}`
    if (handledRef.current === signature) {
      return
    }
    handledRef.current = signature

    void queryClient.invalidateQueries({ queryKey: ['orders'] })
    void queryClient.invalidateQueries({ queryKey: ['order'] })
    void queryClient.invalidateQueries({ queryKey: queryKeys.activity(activityId) })
    void queryClient.invalidateQueries({ queryKey: queryKeys.activities() })
    void queryClient.invalidateQueries({ queryKey: queryKeys.rank(activityId) })
  }, [activityId, orderNo, orderStatus, queryClient])

  const hint = phaseHint(phase)
  const canBuy = phase === 'IN_PROGRESS'
  const submitting = mutation.isPending

  const onClick = () => {
    setErrorMessage(null)
    if (!user) {
      navigate('/login', { state: { from: `/activities/${activityId}` } })
      return
    }
    mutation.mutate()
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between text-sm">
        <span className="text-ink-600">限购</span>
        <span className="tnum font-medium text-ink-900">{limitPerUser ?? 1} 件 / 人</span>
      </div>

      <Button
        block
        size="lg"
        onClick={onClick}
        loading={submitting}
        disabled={!canBuy}
        icon={<ShoppingBag className="size-5" aria-hidden />}
        className={!canBuy ? 'cursor-not-allowed' : undefined}
      >
        {canBuy ? '立即秒杀' : PHASE_TEXT[phase]}
      </Button>

      {hint ? <p className="text-center text-xs text-ink-600">{hint}</p> : null}
      {!user ? <p className="text-center text-xs text-ink-400">登录后即可参与</p> : null}

      <Dialog.Root open={dialogOpen} onOpenChange={setDialogOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-overlay bg-ink-900/40 animate-in fade-in" />
          <Dialog.Content className="fixed left-1/2 top-1/2 z-overlay w-[calc(100%-2rem)] max-w-sm -translate-x-1/2 -translate-y-1/2 rounded-xl border border-state-line bg-white p-6 shadow-card animate-in zoom-in-95">
            <div className="flex items-start justify-between">
              <Dialog.Title className="text-lg font-bold text-ink-900">
                {errorMessage ? '秒杀未提交成功' : '秒杀结果'}
              </Dialog.Title>
              <Dialog.Close asChild>
                <button
                  type="button"
                  aria-label="关闭"
                  className="cursor-pointer rounded p-1 text-ink-400 transition-colors duration-150 hover:text-ink-900 focus-ring"
                >
                  <X className="size-4" aria-hidden />
                </button>
              </Dialog.Close>
            </div>

            {errorMessage ? (
              <p className="mt-3 rounded-md bg-brand-soft px-3 py-2 text-sm text-state-danger">
                {errorMessage}
              </p>
            ) : result ? (
              <div className="mt-3 space-y-3">
                <Badge tone={result.result === 'QUEUED' ? 'info' : 'danger'}>
                  {SECKILL_RESULT_TEXT[result.result]}
                </Badge>

                {orderNo ? (
                  <div className="rounded-lg bg-page p-3 text-sm">
                    <p className="text-ink-600">订单号</p>
                    <p className="tnum mt-0.5 break-all font-medium text-ink-900">{orderNo}</p>
                    <p className="mt-2 flex items-center gap-1.5 text-ink-600">
                      {orderQuery.isFetching ? <Loader2 className="size-3.5 animate-spin" aria-hidden /> : null}
                      状态：{orderStatus ? (STATUS_TEXT[orderStatus] ?? orderStatus) : '查询中…'}
                    </p>

                    {orderQuery.pollingStopped ? (
                      <div className="mt-2 rounded-md bg-white px-2 py-2">
                        <p className="text-xs text-ink-600">
                          自动刷新已停止（超时或连续失败）。订单可能仍在处理中，可手动刷新查看。
                        </p>
                        <Button
                          variant="secondary"
                          size="sm"
                          className="mt-2"
                          loading={orderQuery.isFetching}
                          icon={<RefreshCw className="size-3.5" aria-hidden />}
                          onClick={() => void orderQuery.refetch()}
                        >
                          手动刷新状态
                        </Button>
                      </div>
                    ) : null}
                  </div>
                ) : null}

                {orderStatus === 'CREATED' ? (
                  <Button block onClick={() => navigate('/orders')}>
                    查看我的订单
                  </Button>
                ) : null}
              </div>
            ) : null}
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  )
}
