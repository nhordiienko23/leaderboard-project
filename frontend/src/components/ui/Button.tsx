import type { ButtonHTMLAttributes, ReactNode } from 'react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  loading?: boolean
  icon?: ReactNode
}

const variantClasses = {
  primary:
    'border-[#7C3AED] bg-[#7C3AED] text-white hover:border-[#8B5CF6] hover:bg-[#8B5CF6]',
  secondary:
    'border-[#2A2F3D] bg-[#171A23] text-[#F4F4F5] hover:border-[#71717A] hover:bg-[#1C202C]',
  ghost:
    'border-transparent bg-transparent text-[#A1A1AA] hover:bg-[#1C202C] hover:text-[#F4F4F5]',
  danger:
    'border-[#EF4444] bg-[#EF4444] text-white hover:border-[#F87171] hover:bg-[#F87171]',
}

export function Button({
  variant = 'primary',
  loading = false,
  icon,
  disabled,
  className = '',
  children,
  ...props
}: ButtonProps) {
  const isDisabled = disabled || loading

  return (
    <button
      type="button"
      disabled={isDisabled}
      aria-busy={loading || undefined}
      className={`inline-flex min-h-10 items-center justify-center gap-2 rounded-lg border px-4 py-2 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:border-[#2A2F3D] disabled:bg-[#1C202C] disabled:text-[#71717A] ${variantClasses[variant]} ${className}`}
      {...props}
    >
      {loading ? (
        <span
          aria-hidden="true"
          className="h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent"
        />
      ) : (
        icon
      )}
      {children}
    </button>
  )
}

