import { useCallback, useEffect, useRef, useState } from 'react'
import { getComments } from '../../api/comments'
import { normalizeApiError } from '../../api/errors'
import type { CommentDTO, PageResponse } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Alert, Button, EmptyState, Spinner } from '../../components/ui'
import { Pagination } from '../../components/ui/Pagination'
import { CommentComposer } from './CommentComposer'
import { CommentItem } from './CommentItem'

const COMMENTS_PAGE_SIZE = 5

interface CommentListProps {
  postId: number
  onEngagementChanged?: () => void
}

export function CommentList({ postId, onEngagementChanged }: CommentListProps) {
  const auth = useAuth()
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<CommentDTO> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [status, setStatus] = useState<number | null>(null)
  const requestRef = useRef(0)
  const mountedRef = useRef(false)
  const pageRef = useRef(page)
  pageRef.current = page

  const load = useCallback(async (pageNumber: number) => {
    if (!mountedRef.current) return
    const requestId = ++requestRef.current
    setLoading(true)
    setError(undefined)
    setStatus(null)
    let movingBack = false
    try {
      const response = await getComments(postId, { page: pageNumber, size: COMMENTS_PAGE_SIZE, sort: 'id,asc' })
      if (requestRef.current === requestId) {
        if (pageNumber > 0 && response.data.content.length === 0) {
          movingBack = true
          setPage(Math.min(pageNumber - 1, Math.max(0, response.data.totalPages - 1)))
        } else {
          setData(response.data)
        }
      }
    } catch (cause) {
      if (requestRef.current === requestId) {
        const apiError = normalizeApiError(cause)
        setStatus(apiError.status)
        setError(apiError.message)
        setData(null)
      }
    } finally {
      if (requestRef.current === requestId && !movingBack) setLoading(false)
    }
  }, [postId])

  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false }
  }, [])

  useEffect(() => {
    void load(page)
    return () => { requestRef.current += 1 }
  }, [load, page])

  const onCreated = () => {
    onEngagementChanged?.()
    void load(pageRef.current)
  }

  const onUpdated = (updated: CommentDTO) => {
    setData((current) => current ? { ...current, content: current.content.map((comment) => comment.id === updated.id ? updated : comment) } : current)
  }

  const onDeleted = () => {
    onEngagementChanged?.()
    void load(pageRef.current)
  }

  return (
    <section id="comments" className="mt-8 scroll-mt-6" aria-labelledby="comments-heading">
      <h2 id="comments-heading" className="mb-4 text-2xl font-semibold">Comments</h2>

      {auth.isAuthenticated && <div className="mb-4"><CommentComposer postId={postId} onCreated={onCreated} /></div>}

      {loading && <div className="flex min-h-32 items-center justify-center" role="status" aria-label="Loading comments"><Spinner /></div>}

      {!loading && error && (
        <div className="space-y-3">
          {status === 404 ? (
            <Alert tone="warning" title="Comments not found">This post or comment resource is no longer available.</Alert>
          ) : status === 403 ? (
            <Alert tone="error" title="Forbidden">You do not have permission to view comments.</Alert>
          ) : (
            <Alert tone="error" title="Unable to load comments">{error}</Alert>
          )}
          <Button variant="secondary" onClick={() => void load(page)}>Retry</Button>
        </div>
      )}

      {!loading && !error && data?.content.length === 0 && (
        <EmptyState title="No comments yet" description="Be the first to add a comment." />
      )}

      {!loading && !error && data && data.content.length > 0 && (
        <>
          <div className="grid gap-3">
            {data.content.map((comment) => (
              <CommentItem key={comment.id} comment={comment} onUpdated={onUpdated} onDeleted={onDeleted} />
            ))}
          </div>
          <div className="mt-5">
            <Pagination currentPage={page} totalPages={data.totalPages} onPageChange={setPage} />
          </div>
        </>
      )}
    </section>
  )
}
