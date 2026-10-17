import { useState } from 'react'
import { Flame, RefreshCcw, Rocket } from 'lucide-react'
import { AdminShell } from '../../components/admin/AdminShell'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { toast } from '../../components/ui/Toast'
import { useAdminToken } from '../../stores/adminToken'
import { createActivity, preheatActivity, resetActivityStock } from '../../api/admin'
import { toMessage } from '../../lib/errors'

/**
 * `<input type="datetime-local">` 给的是 `2026-10-17T21:09`，
 * 而后端 `ActivityRequest` 用 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`
 * ⇒ 必须转成**空格分隔**并补秒，否则 40001（联调时踩过）。
 */
const toServerTime = (local: string) => {
  const withSeconds = local.length === 16 ? `${local}:00` : local
  return withSeconds.replace('T', ' ')
}

/** 活动创建 + 预热 + 重置库存（管理端三步闭环）。 */
export default function AdminActivityPage() {
  const { token } = useAdminToken()

  const [productId, setProductId] = useState('')
  const [activityName, setActivityName] = useState('')
  const [startTime, setStartTime] = useState('')
  const [endTime, setEndTime] = useState('')
  const [priceYuan, setPriceYuan] = useState('')
  const [stock, setStock] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [activityId, setActivityId] = useState('')
  const [acting, setActing] = useState<'preheat' | 'reset' | null>(null)

  const onCreate = (event: React.FormEvent) => {
    event.preventDefault()
    const price = Number(priceYuan)
    const seckillStock = Number(stock)

    if (!productId.trim() || !startTime || !endTime || !priceYuan || !stock) {
      toast.error('商品 ID、开始/结束时间、秒杀价、库存均为必填')
      return
    }
    if (!Number.isFinite(price) || price <= 0) {
      toast.error('秒杀价必须大于 0')
      return
    }
    if (!Number.isInteger(seckillStock) || seckillStock <= 0) {
      toast.error('秒杀库存必须是正整数')
      return
    }

    setSubmitting(true)
    createActivity(token, {
      productId: productId.trim(),
      activityName: activityName.trim() || '限时秒杀',
      startTime: toServerTime(startTime),
      endTime: toServerTime(endTime),
      seckillPrice: Math.round(price * 100),
      seckillStock,
      limitPerUser: 1,
    })
      .then((result) => {
        const id = result.activityId ? String(result.activityId) : ''
        setActivityId(id)
        toast.success(id ? `活动已创建（ID ${id}）` : '活动已创建')
      })
      .catch((error: unknown) => {
        toast.error(toMessage(error))
        console.error('创建活动失败', error)
      })
      .finally(() => setSubmitting(false))
  }

  const runAction = (kind: 'preheat' | 'reset') => {
    if (!activityId.trim()) {
      toast.error('请先填写活动 ID')
      return
    }
    setActing(kind)
    const action = kind === 'preheat' ? preheatActivity : resetActivityStock
    action(token, activityId.trim())
      .then(() => toast.success(kind === 'preheat' ? '库存已预热到 Redis' : '库存已重置'))
      .catch((error: unknown) => {
        toast.error(toMessage(error))
        console.error('活动操作失败', error)
      })
      .finally(() => setActing(null))
  }

  return (
    <AdminShell>
      <div className="container-page max-w-2xl space-y-5 py-8">
        <div>
          <h1 className="text-heading text-ink-900">活动管理</h1>
          <p className="mt-1.5 text-sm text-ink-600">
            创建活动后必须「预热」才会写入 Redis 库存；重置库存会按活动配置重新灌入。
          </p>
        </div>

        <Card title="创建秒杀活动">
          <form className="space-y-4" onSubmit={onCreate} noValidate>
            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                label="商品 ID"
                name="productId"
                placeholder="先到「商品录入」创建商品"
                value={productId}
                onChange={(event) => setProductId(event.target.value)}
              />
              <Input
                label="活动名称"
                name="activityName"
                placeholder="例如：周年庆整点秒杀"
                value={activityName}
                onChange={(event) => setActivityName(event.target.value)}
              />
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                label="开始时间"
                name="startTime"
                type="datetime-local"
                value={startTime}
                onChange={(event) => setStartTime(event.target.value)}
              />
              <Input
                label="结束时间"
                name="endTime"
                type="datetime-local"
                value={endTime}
                onChange={(event) => setEndTime(event.target.value)}
              />
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                label="秒杀价（元）"
                name="priceYuan"
                type="number"
                min="0.01"
                step="0.01"
                placeholder="例如 99.00"
                value={priceYuan}
                onChange={(event) => setPriceYuan(event.target.value)}
                hint="自动换算为「分」"
              />
              <Input
                label="秒杀库存（件）"
                name="stock"
                type="number"
                min="1"
                step="1"
                placeholder="例如 50"
                value={stock}
                onChange={(event) => setStock(event.target.value)}
                hint="限购固定 1 件 / 人"
              />
            </div>

            <Button
              type="submit"
              block
              loading={submitting}
              icon={<Flame className="size-4" aria-hidden />}
            >
              创建活动
            </Button>
          </form>
        </Card>

        <Card title="预热与库存">
          <div className="space-y-3">
            <Input
              label="活动 ID"
              name="activityId"
              placeholder="创建成功后自动填入，也可手动填写"
              value={activityId}
              onChange={(event) => setActivityId(event.target.value)}
            />
            <div className="grid gap-2 sm:grid-cols-2">
              <Button
                loading={acting === 'preheat'}
                icon={<Rocket className="size-4" aria-hidden />}
                onClick={() => runAction('preheat')}
              >
                预热库存
              </Button>
              <Button
                variant="secondary"
                loading={acting === 'reset'}
                icon={<RefreshCcw className="size-4" aria-hidden />}
                onClick={() => runAction('reset')}
              >
                重置库存
              </Button>
            </div>
          </div>
        </Card>
      </div>
    </AdminShell>
  )
}
