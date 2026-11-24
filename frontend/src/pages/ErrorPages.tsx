import { Link } from 'react-router-dom'
import { ShieldAlert, SearchX } from 'lucide-react'
import { Card } from '../components/ui/Card'
import { Button } from '../components/ui/Button'

export function ForbiddenPage() {
  return (
    <Card className="mx-auto max-w-xl p-8 text-center">
      <ShieldAlert className="mx-auto h-10 w-10 text-lb-primary-hover" aria-hidden="true" />
      <h1 className="mt-4 text-3xl font-bold">Permission denied</h1>
      <p className="mt-3 text-lb-muted">You do not have permission to access this page.</p>
      <Link to="/posts" className="mt-6 inline-flex"><Button>Go to posts</Button></Link>
    </Card>
  )
}

export function NotFoundPage() {
  return (
    <Card className="mx-auto max-w-xl p-8 text-center">
      <SearchX className="mx-auto h-10 w-10 text-lb-primary-hover" aria-hidden="true" />
      <h1 className="mt-4 text-3xl font-bold">Page not found</h1>
      <p className="mt-3 text-lb-muted">The page you requested does not exist.</p>
      <Link to="/posts" className="mt-6 inline-flex"><Button>Go to posts</Button></Link>
    </Card>
  )
}

