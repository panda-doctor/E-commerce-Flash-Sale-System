import { Link, NavLink, useNavigate } from 'react-router-dom'
import { Flame, LogOut, PackageSearch, ReceiptText, ShieldCheck, Timer, UserRound } from 'lucide-react'
import { cn } from '../../lib/cn'
import { useAuth } from '../../stores/auth'
import { toast } from '../ui/Toast'

const NAV_ITEMS = [
  { to: '/', label: '首页', icon: Flame, end: true },
  { to: '/products', label: '全部商品', icon: PackageSearch },
  { to: '/activities', label: '秒杀活动', icon: Timer },
  { to: '/orders', label: '我的订单', icon: ReceiptText },
]

/** 顶部固定导航（高度 64px，主内容区需 pt-16）。 */
export function TopNav() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const onLogout = () => {
    logout()
      .then(() => {
        toast.info('已退出登录')
        navigate('/login', { replace: true })
      })
      .catch((error: unknown) => console.error('退出登录失败', error))
  }

  return (
    <header className="fixed inset-x-0 top-0 z-nav border-b border-state-line bg-white/95 backdrop-blur">
      <div className="container-page flex h-16 items-center gap-4">
        <Link to="/" className="flex items-center gap-2 rounded-md focus-ring">
          <span className="flex size-8 items-center justify-center rounded-lg bg-brand text-sm font-bold text-white">
            闪
          </span>
          <span className="hidden text-lg font-bold tracking-tight text-ink-900 sm:block">
            闪购秒杀
          </span>
        </Link>

        <nav className="hidden items-center gap-1 md:flex" aria-label="主导航">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-1.5 rounded-md px-3 py-2 text-sm transition-colors duration-150',
                  isActive
                    ? 'bg-brand-soft font-semibold text-brand'
                    : 'text-ink-600 hover:bg-page hover:text-ink-900',
                )
              }
            >
              <item.icon className="size-4" aria-hidden />
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-1.5">
          <Link
            to="/admin"
            className="hidden items-center gap-1.5 rounded-md px-2.5 py-2 text-sm text-ink-600 transition-colors duration-150 hover:bg-page hover:text-ink-900 sm:flex focus-ring"
          >
            <ShieldCheck className="size-4" aria-hidden />
            管理后台
          </Link>

          {user ? (
            <div className="flex items-center gap-1.5">
              <Link
                to="/profile"
                className="flex items-center gap-2 rounded-md px-2.5 py-1.5 transition-colors duration-150 hover:bg-page focus-ring"
              >
                <span className="flex size-7 items-center justify-center rounded-full bg-brand-soft text-brand">
                  <UserRound className="size-4" aria-hidden />
                </span>
                <span className="hidden max-w-[7rem] truncate text-sm font-medium text-ink-900 sm:block">
                  {user.displayName ?? user.accountName}
                </span>
              </Link>
              <button
                type="button"
                onClick={onLogout}
                aria-label="退出登录"
                className="cursor-pointer rounded-md p-2 text-ink-400 transition-colors duration-150 hover:bg-page hover:text-state-danger focus-ring"
              >
                <LogOut className="size-4" aria-hidden />
              </button>
            </div>
          ) : (
            <Link
              to="/login"
              className="rounded-md bg-brand px-4 py-2 text-sm font-semibold text-white transition-colors duration-150 hover:bg-brand-dark focus-ring"
            >
              登录 / 注册
            </Link>
          )}
        </div>
      </div>
    </header>
  )
}
