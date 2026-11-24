import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { normalizeApiError, type ApiError } from '../../api/errors'
import { getUser } from '../../api/users'
import type { UserResponse } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { Spinner } from '../../components/ui/Spinner'
import { ForbiddenPage, NotFoundPage } from '../ErrorPages'

function formatDate(value: string) {
  return new Date(value).toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

export function UserProfilePage() {
  const { id: idParam } = useParams()
  const userId = idParam && /^[1-9]\d*$/.test(idParam) ? Number(idParam) : NaN
  const [user, setUser] = useState<UserResponse | null>(null)
  const [loading, setLoading] = useState(Number.isSafeInteger(userId))
  const [error, setError] = useState<ApiError | null>(null)
  const [retry, setRetry] = useState(0)
  const requestRef = useRef(0)

  const load = useCallback(async () => {
    if (!Number.isSafeInteger(userId)) return

    const requestId = ++requestRef.current
    setLoading(true)
    setError(null)

    try {
      const response = await getUser(userId)
      if (requestRef.current === requestId) setUser(response.data)
    } catch (cause) {
      if (requestRef.current === requestId) {
        setError(normalizeApiError(cause))
        setUser(null)
      }
    } finally {
      if (requestRef.current === requestId) setLoading(false)
    }
  }, [userId])

  useEffect(() => {
    if (!Number.isSafeInteger(userId)) return
    void load()
    return () => { requestRef.current += 1 }
  }, [load, retry, userId])

  if (!Number.isSafeInteger(userId) || error?.status === 404) return <NotFoundPage />
  if (error?.status === 403) return <ForbiddenPage />

  return (
    <section>
      <div className="mb-5">
        <Link to="/users" className="text-sm font-medium text-lb-primary-hover hover:underline">
          ← Back to users
        </Link>
      </div>

      {loading && (
        <div className="flex min-h-48 items-center justify-center" role="status" aria-label="Loading profile">
          <Spinner />
        </div>
      )}

      {!loading && error && (
        <div className="space-y-3">
          <Alert tone="error" title="Unable to load profile">{error.message}</Alert>
          <Button variant="secondary" onClick={() => setRetry((current) => current + 1)}>Retry</Button>
        </div>
      )}

      {!loading && !error && user && (
        <Card className="mx-auto max-w-2xl p-6 sm:p-8">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-lb-primary-hover">Community profile</p>
              <h1 className="mt-2 break-words text-3xl font-bold">{user.username}</h1>
            </div>
            <Badge tone={user.role === 'ADMIN' ? 'primary' : 'neutral'}>{user.role}</Badge>
          </div>

          <dl className="mt-7 grid gap-5 border-t border-lb-border pt-6 sm:grid-cols-2">
            <div>
              <dt className="text-sm text-lb-muted">Email</dt>
              <dd className="mt-1 break-all font-medium">{user.email}</dd>
            </div>
            <div>
              <dt className="text-sm text-lb-muted">Member since</dt>
              <dd className="mt-1 font-medium">
                <time dateTime={user.registeredAt}>{formatDate(user.registeredAt)}</time>
              </dd>
            </div>
            <div>
              <dt className="text-sm text-lb-muted">Last login</dt>
              <dd className="mt-1 font-medium">
                {user.lastLoginAt ? (
                  <time dateTime={user.lastLoginAt}>{formatDate(user.lastLoginAt)}</time>
                ) : (
                  'No login recorded'
                )}
              </dd>
            </div>
            <div>
              <dt className="text-sm text-lb-muted">Profile updated</dt>
              <dd className="mt-1 font-medium">
                <time dateTime={user.updatedAt}>{formatDate(user.updatedAt)}</time>
              </dd>
            </div>
          </dl>
        </Card>
      )}
    </section>
  )
}
