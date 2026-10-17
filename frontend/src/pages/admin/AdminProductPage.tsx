import { useState } from 'react'
import { PackagePlus } from 'lucide-react'
import { AdminShell } from '../../components/admin/AdminShell'
import { ImageUploader } from '../../components/admin/ImageUploader'
import { Card } from '../../components/ui/Card'
import { Button } from '../../components/ui/Button'
import { Input } from '../../components/ui/Input'
import { toast } from '../../components/ui/Toast'
import { useAdminToken } from '../../stores/adminToken'
import { saveProduct } from '../../api/admin'
import { toMessage } from '../../lib/errors'

// 真实枚举：ProductStatusEnum.ON_SHELF / OFF_SHELF
const STATUS_OPTIONS = [
  { value: 'ON_SHELF', label: '上架在售' },
  { value: 'OFF_SHELF', label: '下架' },
]

/** 商品录入：原价以「元」输入，提交前转换成后端要求的「分」。 */
export default function AdminProductPage() {
  const { token } = useAdminToken()

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [imageUrl, setImageUrl] = useState('')
  const [priceYuan, setPriceYuan] = useState('')
  const [totalStock, setTotalStock] = useState('')
  const [status, setStatus] = useState('ON_SHELF')
  const [submitting, setSubmitting] = useState(false)
  const [createdId, setCreatedId] = useState<string | null>(null)

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    const price = Number(priceYuan)
    const stock = Number(totalStock)

    if (!name.trim() || !priceYuan || !totalStock) {
      toast.error('商品名称、原价、库存为必填项')
      return
    }
    if (!Number.isFinite(price) || price <= 0) {
      toast.error('原价必须大于 0')
      return
    }
    if (!Number.isInteger(stock) || stock < 0) {
      toast.error('库存必须是非负整数')
      return
    }

    setSubmitting(true)
    saveProduct(token, {
      name: name.trim(),
      description: description.trim() || undefined,
      imageUrl: imageUrl.trim() || undefined,
      originalPrice: Math.round(price * 100),
      totalStock: stock,
      status,
    })
      .then((result) => {
        const id = result.productId ? String(result.productId) : null
        setCreatedId(id)
        toast.success(id ? `商品已保存（ID ${id}）` : '商品已保存')
      })
      .catch((error: unknown) => {
        toast.error(toMessage(error))
        console.error('保存商品失败', error)
      })
      .finally(() => setSubmitting(false))
  }

  return (
    <AdminShell>
      <div className="container-page max-w-2xl space-y-5 py-8">
        <div>
          <h1 className="text-heading text-ink-900">商品录入</h1>
          <p className="mt-1.5 text-sm text-ink-600">
            提交到 POST /api/admin/products；图片先上传拿到 URL 再随商品保存。
          </p>
        </div>

        <Card>
          <form className="space-y-4" onSubmit={onSubmit} noValidate>
            <Input
              label="商品名称"
              name="name"
              placeholder="例如：无线降噪耳机 Pro"
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
            <div className="space-y-1.5">
              <label htmlFor="description" className="text-sm font-medium text-ink-900">
                商品描述
              </label>
              <textarea
                id="description"
                rows={3}
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                placeholder="描述卖点、材质、规格等，会展示在商品详情页"
                className="w-full rounded-md border border-state-line bg-white p-3 text-sm text-ink-900 outline-none transition-colors duration-150 placeholder:text-ink-400 focus:border-brand"
              />
            </div>

            <ImageUploader value={imageUrl} onChange={setImageUrl} />

            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                label="原价（元）"
                name="priceYuan"
                type="number"
                min="0.01"
                step="0.01"
                placeholder="例如 299.00"
                value={priceYuan}
                onChange={(event) => setPriceYuan(event.target.value)}
                hint="提交时自动换算为「分」"
              />
              <Input
                label="总库存（件）"
                name="totalStock"
                type="number"
                min="0"
                step="1"
                placeholder="例如 100"
                value={totalStock}
                onChange={(event) => setTotalStock(event.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <label htmlFor="status" className="text-sm font-medium text-ink-900">
                商品状态
              </label>
              <select
                id="status"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
                className="h-11 w-full cursor-pointer rounded-md border border-state-line bg-white px-3 text-sm text-ink-900 outline-none focus:border-brand"
              >
                {STATUS_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>

            <Button
              type="submit"
              block
              loading={submitting}
              icon={<PackagePlus className="size-4" aria-hidden />}
            >
              保存商品
            </Button>
          </form>

          {createdId ? (
            <p className="mt-4 rounded-md bg-page px-3 py-2 text-xs text-ink-600">
              新商品 ID：<span className="tnum font-semibold text-ink-900">{createdId}</span>
              （创建活动时需要填写）
            </p>
          ) : null}
        </Card>
      </div>
    </AdminShell>
  )
}
