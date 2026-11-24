import type { ReactNode } from 'react'

interface AlertProps {
  tone?: 'neutral' | 'success' | 'warning' | 'error'
  title?: string
  children: ReactNode
}

const tones = {
  neutral: 'border-[#2A2F3D] bg-[#171A23] text-[#A1A1AA]',
  success: 'border-[#22C55E]/40 bg-[#22C55E]/10 text-[#86EFAC]',
  warning: 'border-[#F59E0B]/40 bg-[#F59E0B]/10 text-[#FCD34D]',
  error: 'border-[#EF4444]/40 bg-[#EF4444]/10 text-[#FCA5A5]',
}

export function Alert({ tone = 'neutral', title, children }: AlertProps) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={`rounded-lg border px-4 py-3 ${tones[tone]}`}
    >
      {title && <p className="mb-1 font-semibold text-[#F4F4F5]">{title}</p>}
      <div className="text-sm">{children}</div>
    </div>
  )
}

