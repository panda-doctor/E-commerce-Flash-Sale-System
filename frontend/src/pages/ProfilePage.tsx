import { Link, useNavigate } from 'react-router-dom'
import { LogOut, ReceiptText, ShoppingBag, UserRound } from 'lucide-react'
import { useAuth } from '../stores/auth'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { toast } from '../components/ui/Toast'
import { formatDateTime } from '../lib/format'
import { toMessage } from '../lib/errors'

/** 个人中心：展示真实账户字段（来自 /api/auth/me）。 */
export default function ProfilePage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  if (!user) {
    return null
  }

  const onLogout = () => {
    logout()
      .then((serverConfirmed) => {
        if (serverConfirmed) {
          toast.info('已退出登录')
        } else {
          toast.error('已退出当前页面，但服务器会话可能仍未注销，请检查网络后重试')
        }
        navigate('/login', { replace: true })
      })
      .catch((error: unknown) => {
        toast.error(toMessage(error))
        console.error('退出登录失败', error)
      })
  }

  return (
    <div className="container-page max-w-3xl py-10">
      <h1 className="text-heading text-ink-900">个人中心</h1>
      <p className="mt-1.5 text-sm text-ink-600">这里的信息来自后端会话（GET /api/auth/me）。</p>

      <Card className="mt-6" padded={false}>
        <div className="flex items-center gap-4 p-6">
          <div className="flex size-14 items-center justify-center rounded-full bg-page text-ink-900">
            <UserRound className="size-7" aria-hidden />
          </div>
          <div className="flex-1">
            <p className="text-lg font-semibold text-ink-900">
              {user.displayName ?? user.accountName}
            </p>
            <p className="tnum text-sm text-ink-600">账号：{user.accountName}</p>
          </div>
          <Badge tone={user.status === 1 ? 'success' : 'neutral'}>
            {user.status === 1 ? '正常' : `状态 ${user.status}`}
          </Badge>
        </div>

        <dl className="grid gap-4 border-t border-state-line p-6 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-ink-600">用户 ID</dt>
            <dd className="tnum mt-1 font-medium text-ink-900">{user.id}</dd>
          </div>
          <div>
            <dt className="text-ink-600">注册时间</dt>
            <dd className="tnum mt-1 font-medium text-ink-900">{formatDateTime(user.createdAt)}</dd>
          </div>
        </dl>
      </Card>

      <div className="mt-6 grid gap-3 sm:grid-cols-3">
        <Link to="/orders">
          <Button variant="secondary" block icon={<ReceiptText className="size-4" aria-hidden />}>
            我的订单
          </Button>
        </Link>
        <Link to="/products">
          <Button variant="secondary" block icon={<ShoppingBag className="size-4" aria-hidden />}>
            继续逛商品
          </Button>
        </Link>
        <Button
          variant="ghost"
          block
          icon={<LogOut className="size-4" aria-hidden />}
          onClick={onLogout}
        >
          退出登录
        </Button>
      </div>
    </div>
  )
}
