import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ErrorBoundary } from '../ErrorBoundary'

function Boom(): JSX.Element {
  throw new Error('渲染炸了')
}

describe('ErrorBoundary', () => {
  it('子组件抛错时展示兜底界面，而不是白屏', () => {
    // React 会把捕获到的错误打到 console.error，这里静音避免污染测试输出
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined)

    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    )

    expect(screen.getByText('页面出现异常')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '重试' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /回到首页/ })).toBeInTheDocument()

    spy.mockRestore()
  })

  it('正常子组件原样渲染', () => {
    render(
      <ErrorBoundary>
        <p>内容正常</p>
      </ErrorBoundary>,
    )
    expect(screen.getByText('内容正常')).toBeInTheDocument()
  })
})
