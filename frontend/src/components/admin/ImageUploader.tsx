import { useState } from 'react'
import { ImagePlus, Loader2 } from 'lucide-react'
import { uploadImage } from '../../api/admin'
import { useAdminToken } from '../../stores/adminToken'
import { Button } from '../ui/Button'
import { toast } from '../ui/Toast'
import { toMessage } from '../../lib/errors'

const ACCEPT = 'image/jpeg,image/png,image/gif,image/webp,image/bmp,.jfif'
const MAX_BYTES = 5 * 1024 * 1024

/**
 * 图片上传：走后端 multipart 接口（字段名 file，5MB 上限，服务端做魔数校验）。
 * 前端只做体积预检与预览，格式最终以后端判定为准。
 */
export function ImageUploader({
  value,
  onChange,
}: {
  value: string
  onChange: (url: string) => void
}) {
  const { token } = useAdminToken()
  const [uploading, setUploading] = useState(false)

  const onFile = (file: File | undefined) => {
    if (!file) {
      return
    }
    if (file.size > MAX_BYTES) {
      toast.error('图片不能超过 5MB')
      return
    }
    setUploading(true)
    uploadImage(token, file)
      .then((result) => {
        onChange(result.url)
        toast.success('图片上传成功')
      })
      .catch((error: unknown) => {
        toast.error(toMessage(error))
        console.error('图片上传失败', error)
      })
      .finally(() => setUploading(false))
  }

  return (
    <div className="space-y-2">
      <p className="text-sm font-medium text-ink-900">商品图片</p>
      <div className="flex items-center gap-4">
        <div className="size-24 shrink-0 overflow-hidden rounded-lg border border-state-line bg-page">
          {value ? (
            <img src={value} alt="商品图预览" className="size-full object-cover" />
          ) : (
            <div className="flex size-full items-center justify-center text-ink-400">
              <ImagePlus className="size-6" aria-hidden />
            </div>
          )}
        </div>
        <div className="flex-1 space-y-1.5">
          <label className="inline-flex cursor-pointer items-center gap-2 rounded-md border border-state-line bg-white px-3 py-2 text-sm text-ink-900 transition-colors duration-150 hover:border-brand focus-within:ring-2 focus-within:ring-brand">
            {uploading ? (
              <Loader2 className="size-4 animate-spin" aria-hidden />
            ) : (
              <ImagePlus className="size-4" aria-hidden />
            )}
            {uploading ? '上传中…' : '选择图片'}
            <input
              type="file"
              accept={ACCEPT}
              className="hidden"
              disabled={uploading}
              onChange={(event) => onFile(event.target.files?.[0])}
            />
          </label>
          <p className="text-xs text-ink-400">支持 jpg/png/gif/webp/bmp，单张 ≤ 5MB</p>
        </div>
      </div>
      <input
        className="h-9 w-full rounded-md border border-state-line bg-white px-2 text-xs text-ink-600 outline-none focus:border-brand"
        value={value}
        placeholder="或直接粘贴图片地址 /uploads/…"
        onChange={(event) => onChange(event.target.value)}
        aria-label="图片地址"
      />
      {value ? (
        <Button size="sm" variant="ghost" onClick={() => onChange('')}>
          清除图片
        </Button>
      ) : null}
    </div>
  )
}
