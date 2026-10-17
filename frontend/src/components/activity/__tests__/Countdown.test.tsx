import { describe, expect, it, vi } from 'vitest'
import { render } from '@testing-library/react'
import { Countdown } from '../Countdown'

/**
 * 回归：归零后父组件每次重渲染都会传新的内联 `onFinish`。
 * 修复前 effect 依赖包含该函数，导致每次渲染都回调一次（refetch → 重渲染 → 再 refetch）。
 */
describe('Countdown 归零回调', () => {
  it('多次渲染只回调一次（不再自激刷新）', () => {
    const onFinish = vi.fn()

    const { rerender } = render(
      <Countdown target="2020-01-01 00:00:00" label="距结束" onFinish={() => onFinish()} />,
    )
    expect(onFinish).toHaveBeenCalledTimes(1)

    // 模拟父组件因数据刷新而重渲染：每次都是新的内联函数
    rerender(<Countdown target="2020-01-01 00:00:00" label="距结束" onFinish={() => onFinish()} />)
    rerender(<Countdown target="2020-01-01 00:00:00" label="距结束" onFinish={() => onFinish()} />)

    expect(onFinish).toHaveBeenCalledTimes(1)
  })

  it('目标未到时不回调', () => {
    const onFinish = vi.fn()
    render(<Countdown target="2099-01-01 00:00:00" label="距结束" onFinish={() => onFinish()} />)
    expect(onFinish).not.toHaveBeenCalled()
  })

  it('无目标时间时安全降级，不抛错', () => {
    const onFinish = vi.fn()
    render(<Countdown target={undefined} label="距结束" onFinish={() => onFinish()} />)
    expect(onFinish).not.toHaveBeenCalled()
  })
})
