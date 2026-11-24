interface SpinnerProps {
  label?: string
  size?: 'sm' | 'md' | 'lg'
}

const sizes = { sm: 'h-4 w-4', md: 'h-6 w-6', lg: 'h-8 w-8' }

export function Spinner({ label = 'Loading', size = 'md' }: SpinnerProps) {
  return (
    <span role="status" aria-label={label} className="inline-flex items-center gap-2 text-[#A1A1AA]">
      <span
        aria-hidden="true"
        className={`${sizes[size]} animate-spin rounded-full border-2 border-[#A1A1AA] border-r-transparent`}
      />
      <span className="text-sm">{label}</span>
    </span>
  )
}
