import type { HTMLAttributes } from 'react'

interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: 'neutral' | 'primary' | 'success' | 'warning' | 'error'
}

const tones = {
  neutral: 'border-[#2A2F3D] bg-[#1C202C] text-[#A1A1AA]',
  primary: 'border-[#7C3AED]/40 bg-[#7C3AED]/15 text-[#C4B5FD]',
  success: 'border-[#22C55E]/40 bg-[#22C55E]/15 text-[#86EFAC]',
  warning: 'border-[#F59E0B]/40 bg-[#F59E0B]/15 text-[#FCD34D]',
  error: 'border-[#EF4444]/40 bg-[#EF4444]/15 text-[#FCA5A5]',
}

export function Badge({ tone = 'neutral', className = '', ...props }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-semibold ${tones[tone]} ${className}`}
      {...props}
    />
  )
}

