import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'
import { AlertTriangle, Home, RotateCcw } from 'lucide-react'
import { Button } from '../ui/Button'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  error: Error | null
}

/**
 * 全局渲染异常兜底。
 *
 * 没有它时，任意一个组件抛错都会让整页白屏，用户只能刷新且不知道发生了什么。
 * 这里用类组件（React 只支持 class 捕获渲染期异常），提供"重试 / 回首页"两条恢复路径。
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // 生产环境可在此上报监控；当前至少保证控制台可追溯
    console.error('页面渲染异常：', error, info.componentStack)
  }

  private handleReset = () => {
    this.setState({ error: null })
  }

  private handleHome = () => {
    window.location.assign('/')
  }

  render() {
    const { error } = this.state
    if (!error) {
      return this.props.children
    }

    return (
      <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 px-6 py-16 text-center">
        <div className="flex size-12 items-center justify-center rounded-full bg-brand-soft">
          <AlertTriangle className="size-6 text-state-danger" aria-hidden />
        </div>
        <h1 className="text-subheading text-ink-900">页面出现异常</h1>
        <p className="max-w-md text-sm text-ink-600">
          当前页面渲染失败了。可以重试一次；如果仍然失败，请回到首页重新进入。
        </p>
        {import.meta.env.DEV ? (
          <pre className="max-w-lg overflow-x-auto rounded-md bg-page px-3 py-2 text-left text-xs text-ink-600">
            {error.message}
          </pre>
        ) : null}
        <div className="flex flex-wrap items-center justify-center gap-2">
          <Button onClick={this.handleReset} icon={<RotateCcw className="size-4" aria-hidden />}>
            重试
          </Button>
          <Button variant="secondary" onClick={this.handleHome} icon={<Home className="size-4" aria-hidden />}>
            回到首页
          </Button>
        </div>
      </div>
    )
  }
}
