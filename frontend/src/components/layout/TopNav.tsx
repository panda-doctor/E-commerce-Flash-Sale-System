import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router-dom'
import * as Dialog from '@radix-ui/react-dialog'
import {
  Flame,
  LogOut,
  Menu,
  PackageSearch,
  ReceiptText,
  ShieldCheck,
  Timer,
  UserRound,
  X,
} from 'lucide-react'
import { cn } from '../../lib/cn'
import { useAuth } from '../../stores/auth'
import { toast } from '../ui/Toast'
import { toMessage } from '../../lib/errors'

const NAV_ITEMS = [
  { to: '/', label: '首页', icon: Flame, end: true },
  { to: '/products', label: '全部商品', icon: PackageSearch },
  { to: '/activities', label: '秒杀活动', icon: Timer },
  { to: '/orders', label: '我的订单', icon: ReceiptText },
]

/** 顶部固定导航（高度 64px，主内容区需 pt-16）。窄屏走抽屉菜单，保证手机端可达全部入口。 */
export function TopNav() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)

  const onLogout = () => {
    logout()
      .then((serverConfirmed) => {
        if (serverConfirmed) {
          toast.info('已退出登录')
        } else {
          // 本地状态已清理，但服务端会话可能仍有效：必须如实告知，避免用户误判
          toast.error('已退出当前页面，但服务器会话可能仍未注销，请检查网络后重试')
        }
        setMenuOpen(false)
        navigate('/login', { replace: true })
      })
      .catch((error: unknown) => {
        toast.error(toMessage(error))
        console.error('退出登录失败', error)
      })
  }

  return (
    <header className="fixed inset-x-0 top-0 z-nav border-b border-state-line bg-page-card/95 backdrop-blur">
      <div className="container-page flex h-16 items-center gap-3">
        <button
          type="button"
          onClick={() => setMenuOpen(true)}
          aria-label="打开导航菜单"
          className="cursor-pointer rounded-md p-2 text-ink-600 transition-colors duration-150 hover:bg-page focus-ring md:hidden"
        >
          <Menu className="size-5" aria-hidden />
        </button>

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
                    ? 'bg-page font-semibold text-ink-900'
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
                <span className="flex size-7 items-center justify-center rounded-full bg-page text-ink-900">
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

      <Dialog.Root open={menuOpen} onOpenChange={setMenuOpen}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-overlay bg-ink-900/40 md:hidden" />
          <Dialog.Content className="fixed inset-y-0 left-0 z-overlay flex w-72 max-w-[85vw] flex-col bg-page-card p-5 shadow-card md:hidden">
            <div className="flex items-center justify-between">
              <Dialog.Title className="text-lg font-bold text-ink-900">导航</Dialog.Title>
              <Dialog.Close asChild>
                <button
                  type="button"
                  aria-label="关闭导航"
                  className="cursor-pointer rounded p-1 text-ink-400 transition-colors duration-150 hover:text-ink-900 focus-ring"
                >
                  <X className="size-5" aria-hidden />
                </button>
              </Dialog.Close>
            </div>

            <nav className="mt-5 flex flex-col gap-1" aria-label="移动端导航">
              {NAV_ITEMS.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  onClick={() => setMenuOpen(false)}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-2 rounded-md px-3 py-2.5 text-sm transition-colors duration-150',
                      isActive ? 'bg-page font-semibold text-ink-900' : 'text-ink-600 hover:bg-page',
                    )
                  }
                >
                  <item.icon className="size-4" aria-hidden />
                  {item.label}
                </NavLink>
              ))}
              <Link
                to="/admin"
                onClick={() => setMenuOpen(false)}
                className="flex items-center gap-2 rounded-md px-3 py-2.5 text-sm text-ink-600 transition-colors duration-150 hover:bg-page"
              >
                <ShieldCheck className="size-4" aria-hidden />
                管理后台
              </Link>
            </nav>

            <div className="mt-auto border-t border-state-line pt-4">
              {user ? (
                <div className="space-y-2">
                  <Link
                    to="/profile"
                    onClick={() => setMenuOpen(false)}
                    className="flex items-center gap-2 rounded-md px-3 py-2.5 text-sm text-ink-600 transition-colors duration-150 hover:bg-page"
                  >
                    <UserRound className="size-4" aria-hidden />
                    {user.displayName ?? user.accountName}
                  </Link>
                  <button
                    type="button"
                    onClick={onLogout}
                    className="flex w-full cursor-pointer items-center gap-2 rounded-md px-3 py-2.5 text-left text-sm text-state-danger transition-colors duration-150 hover:bg-state-danger/10"
                  >
                    <LogOut className="size-4" aria-hidden />
                    退出登录
                  </button>
                </div>
              ) : (
                <Link
                  to="/login"
                  onClick={() => setMenuOpen(false)}
                  className="block rounded-md bg-brand px-4 py-2 text-center text-sm font-semibold text-white"
                >
                  登录 / 注册
                </Link>
              )}
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </header>
  )
}
