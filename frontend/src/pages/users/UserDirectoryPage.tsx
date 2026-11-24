import { useCallback, useEffect, useRef, useState } from 'react'
import { normalizeApiError, type ApiError } from '../../api/errors'
import { getUsers } from '../../api/users'
import type { PageResponse, UserResponse } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { EmptyState } from '../../components/ui/EmptyState'
import { Pagination } from '../../components/ui/Pagination'
import { Spinner } from '../../components/ui/Spinner'
import { UserTable } from '../../features/users/UserTable'
import { ForbiddenPage } from '../ErrorPages'

const PAGE_SIZE = 20

export function UserDirectoryPage() {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<PageResponse<UserResponse> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)
  const [retry, setRetry] = useState(0)
  const requestRef = useRef(0)

  const load = useCallback(async () => {
    const requestId = ++requestRef.current
    setLoading(true)
    setError(null)

    try {
      const response = await getUsers({ page, size: PAGE_SIZE })
      if (requestRef.current === requestId) setResult(response.data)
    } catch (cause) {
      if (requestRef.current === requestId) {
        setError(normalizeApiError(cause))
        setResult(null)
      }
    } finally {
      if (requestRef.current === requestId) setLoading(false)
    }
  }, [page])

  useEffect(() => {
    void load()
    return () => { requestRef.current += 1 }
  }, [load, retry])

  if (error?.status === 403) return <ForbiddenPage />

  return (
    <section>
      <header className="mb-6">
        <h1 className="text-3xl font-bold">Community members</h1>
        <p className="mt-2 text-lb-muted">Browse members and view their profiles.</p>
      </header>

      {loading && (
        <div className="flex min-h-48 items-center justify-center" role="status" aria-label="Loading users">
          <Spinner />
        </div>
      )}

      {!loading && error && (
        <div className="space-y-3">
          <Alert tone="error" title="Unable to load users">{error.message}</Alert>
          <Button variant="secondary" onClick={() => setRetry((current) => current + 1)}>Retry</Button>
        </div>
      )}

      {!loading && !error && result && result.content.length === 0 && (
        <EmptyState
          title="No users found"
          description="There are no community members to display yet."
        />
      )}

      {!loading && !error && result && result.content.length > 0 && (
        <>
          <UserTable users={result.content} />
          <div className="mt-4 space-y-3">
            <p className="text-sm text-lb-muted">
              Page {result.number + 1} of {result.totalPages} · {result.totalElements} members
            </p>
            <Pagination currentPage={page} totalPages={result.totalPages} onPageChange={setPage} />
          </div>
        </>
      )}
    </section>
  )
}
