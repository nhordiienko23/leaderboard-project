import { Card } from '../components/ui/Card'

interface PlaceholderPageProps {
  title: string
  description: string
}

export function PlaceholderPage({ title, description }: PlaceholderPageProps) {
  return (
    <Card className="mx-auto max-w-3xl p-6 sm:p-8">
      <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-lb-primary-hover">Placeholder</p>
      <h1 className="text-3xl font-bold text-lb-text">{title}</h1>
      <p className="mt-3 text-lb-muted">{description}</p>
    </Card>
  )
}

