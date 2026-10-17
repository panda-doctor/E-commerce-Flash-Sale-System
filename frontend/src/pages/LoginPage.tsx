import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { LogIn } from 'lucide-react'
import { AuthLayout } from '../components/auth/AuthLayout'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { toast } from '../components/ui/Toast'
import { useAuth } from '../stores/auth'
import { toMessage } from '../lib/errors'

interface LocationState {
  from?: string
}

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const redirectTo = (location.state as LocationState | null)?.from ?? '/'

  const [accountName, setAccountName] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    if (!accountName.trim() || !password) {
      setError('请填写账号与密码')
      return
    }
    setSubmitting(true)
    setError(null)

    login({ accountName: accountName.trim(), password })
      .then((user) => {
        toast.success(`欢迎回来，${user.displayName ?? user.accountName}`)
        navigate(redirectTo, { replace: true })
      })
      .catch((cause: unknown) => {
        const message = toMessage(cause)
        setError(message)
        console.error('登录失败', cause)
      })
      .finally(() => setSubmitting(false))
  }

  return (
    <AuthLayout
      title="登录账号"
      subtitle="登录后即可参与秒杀并查看排队结果"
      footer={
        <span>
          还没有账号？
          <Link to="/register" className="ml-1 font-semibold text-brand hover:text-brand-dark">
            立即注册
          </Link>
        </span>
      }
    >
      <form className="space-y-4" onSubmit={onSubmit} noValidate>
        <Input
          label="账号名"
          name="accountName"
          autoComplete="username"
          placeholder="请输入账号名"
          value={accountName}
          onChange={(event) => setAccountName(event.target.value)}
        />
        <Input
          label="密码"
          name="password"
          type="password"
          autoComplete="current-password"
          placeholder="请输入密码"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />

        {error ? (
          <p role="alert" className="alert-danger">
            {error}
          </p>
        ) : null}

        <Button type="submit" block loading={submitting} icon={<LogIn className="size-4" aria-hidden />}>
          登录
        </Button>
      </form>
    </AuthLayout>
  )
}
