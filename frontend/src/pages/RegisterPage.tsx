import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { UserPlus } from 'lucide-react'
import { AuthLayout } from '../components/auth/AuthLayout'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { toast } from '../components/ui/Toast'
import { useAuth } from '../stores/auth'
import { toMessage } from '../lib/errors'

/** 与后端 `RegisterRequest` 的校验注解保持一致（不额外臆造规则）。 */
const PASSWORD_MIN = 10
const PASSWORD_MAX = 72
const DISPLAY_NAME_MAX = 64
const ACCOUNT_PATTERN = /^[a-z0-9._-]{3,32}$/i

export default function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()

  const [accountName, setAccountName] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [fieldError, setFieldError] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const validate = () => {
    const next: Record<string, string> = {}
    const account = accountName.trim()
    const display = displayName.trim()

    if (!account) {
      next.accountName = '请填写账号名'
    } else if (!ACCOUNT_PATTERN.test(account)) {
      next.accountName = '账号名须为 3~32 位字母、数字或 . _ -'
    }

    if (display.length > DISPLAY_NAME_MAX) {
      next.displayName = `昵称最多 ${DISPLAY_NAME_MAX} 个字符`
    }

    if (password.length < PASSWORD_MIN) {
      next.password = `密码至少 ${PASSWORD_MIN} 位`
    } else if (password.length > PASSWORD_MAX) {
      next.password = `密码最多 ${PASSWORD_MAX} 位`
    }

    if (password !== confirm) {
      next.confirm = '两次输入的密码不一致'
    }

    setFieldError(next)
    return Object.keys(next).length === 0
  }

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    setError(null)
    if (!validate()) {
      return
    }
    setSubmitting(true)

    register({
      accountName: accountName.trim(),
      password,
      displayName: displayName.trim() || undefined,
    })
      .then((user) => {
        toast.success(`注册成功，${user.displayName ?? user.accountName}`)
        navigate('/', { replace: true })
      })
      .catch((cause: unknown) => {
        setError(toMessage(cause))
        console.error('注册失败', cause)
      })
      .finally(() => setSubmitting(false))
  }

  return (
    <AuthLayout
      title="创建账号"
      subtitle="注册后即可参与限时秒杀，一人一件"
      footer={
        <span>
          已有账号？
          <Link to="/login" className="ml-1 font-semibold text-brand hover:text-brand-dark">
            去登录
          </Link>
        </span>
      }
    >
      <form className="space-y-4" onSubmit={onSubmit} noValidate>
        <Input
          label="账号名"
          name="accountName"
          autoComplete="username"
          placeholder="3~32 位字母、数字或 . _ -"
          value={accountName}
          error={fieldError.accountName}
          onChange={(event) => setAccountName(event.target.value)}
        />
        <Input
          label="昵称（可选）"
          name="displayName"
          placeholder="用于页面展示，最多 64 个字符"
          value={displayName}
          error={fieldError.displayName}
          onChange={(event) => setDisplayName(event.target.value)}
        />
        <Input
          label="密码"
          name="password"
          type="password"
          autoComplete="new-password"
          placeholder={`至少 ${PASSWORD_MIN} 位`}
          value={password}
          error={fieldError.password}
          onChange={(event) => setPassword(event.target.value)}
        />
        <Input
          label="确认密码"
          name="confirm"
          type="password"
          autoComplete="new-password"
          placeholder="再次输入密码"
          value={confirm}
          error={fieldError.confirm}
          onChange={(event) => setConfirm(event.target.value)}
        />

        {error ? (
          <p role="alert" className="rounded-md bg-brand-soft px-3 py-2 text-sm text-state-danger">
            {error}
          </p>
        ) : null}

        <Button type="submit" block loading={submitting} icon={<UserPlus className="size-4" aria-hidden />}>
          注册并登录
        </Button>
      </form>
    </AuthLayout>
  )
}
