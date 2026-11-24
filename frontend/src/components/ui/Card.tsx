import type { HTMLAttributes, ReactNode } from 'react'

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
  elevated?: boolean
}

export function Card({ children, elevated = false, className = '', ...props }: CardProps) {
  return (
    <div
      className={`rounded-xl border border-[#2A2F3D] ${elevated ? 'bg-[#1C202C]' : 'bg-[#171A23]'} shadow-sm ${className}`}
      {...props}
    >
      {children}
    </div>
  )
}

