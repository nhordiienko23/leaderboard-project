import { forwardRef, useId, type InputHTMLAttributes } from 'react'

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  helperText?: string
  error?: string
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, helperText, error, id, className = '', disabled, ...props },
  ref,
) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const helperId = `${inputId}-helper`
  const errorId = `${inputId}-error`
  const describedBy = [
    helperText && helperText !== error ? helperId : undefined,
    error ? errorId : undefined,
  ].filter(Boolean).join(' ') || undefined

  return (
    <div className="flex w-full flex-col gap-2">
      <label htmlFor={inputId} className="text-sm font-medium text-[#F4F4F5]">
        {label}
      </label>
      <input
        {...props}
        ref={ref}
        id={inputId}
        disabled={disabled}
        aria-invalid={Boolean(error) || undefined}
        aria-describedby={describedBy}
        className={`h-11 w-full rounded-lg border border-[#2A2F3D] bg-[#171A23] px-3 text-[#F4F4F5] placeholder:text-[#71717A] transition-colors hover:border-[#71717A] disabled:cursor-not-allowed disabled:bg-[#1C202C] disabled:text-[#71717A] ${error ? 'border-[#EF4444]' : ''} ${className}`}
      />
      {helperText && helperText !== error && (
        <p id={helperId} className="text-sm text-[#A1A1AA]">{helperText}</p>
      )}
      {error && <p id={errorId} className="text-sm text-[#EF4444]">{error}</p>}
    </div>
  )
})
