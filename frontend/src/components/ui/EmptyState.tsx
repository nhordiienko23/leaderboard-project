import type { ReactNode } from 'react'

interface EmptyStateProps {
  title: string
  description?: string
  icon?: ReactNode
  action?: ReactNode
}

export function EmptyState({ title, description, icon, action }: EmptyStateProps) {
  return (
    <section className="flex min-h-48 flex-col items-center justify-center rounded-xl border border-dashed border-[#2A2F3D] bg-[#171A23] px-6 py-10 text-center">
      {icon && <div className="mb-4 text-[#71717A]" aria-hidden="true">{icon}</div>}
      <h2 className="text-lg font-semibold text-[#F4F4F5]">{title}</h2>
      {description && <p className="mt-2 max-w-md text-sm text-[#A1A1AA]">{description}</p>}
      {action && <div className="mt-5">{action}</div>}
    </section>
  )
}

