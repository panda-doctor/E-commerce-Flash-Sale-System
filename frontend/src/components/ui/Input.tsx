import type { InputHTMLAttributes } from 'react'
import { cn } from '../../lib/cn'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string | null
  hint?: string
}

/** 原生 input（不用 div 模拟），label 与错误说明与控件关联。 */
export function Input({ label, error, hint, className, id, ...rest }: InputProps) {
  const inputId = id ?? rest.name ?? 'input'
  const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined

  return (
    <div className="flex flex-col gap-1.5">
      {label ? (
        <label htmlFor={inputId} className="text-sm font-medium text-ink-900">
          {label}
        </label>
      ) : null}
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={cn(
          'h-11 w-full rounded-md border bg-white px-3 text-sm text-ink-900 shadow-none outline-none transition-colors duration-150',
          'placeholder:text-ink-400',
          error
            ? 'border-state-danger focus:border-state-danger'
            : 'border-state-line focus:border-brand',
          className,
        )}
        {...rest}
      />
      {error ? (
        <p id={`${inputId}-error`} className="text-xs text-state-danger">
          {error}
        </p>
      ) : hint ? (
        <p id={`${inputId}-hint`} className="text-xs text-ink-400">
          {hint}
        </p>
      ) : null}
    </div>
  )
}
