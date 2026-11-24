import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { getPosts } from '../../api/posts'
import { normalizeApiError, type ApiError } from '../../api/errors'
import type { PageResponse, PostDTO } from '../../api/types'
import { PostComposer } from '../../features/posts/PostComposer'
import { PostCard } from '../../features/posts/PostCard'
import { Alert, Button, EmptyState, Spinner } from '../../components/ui'
import { Pagination } from '../../components/ui/Pagination'

const POSTS_PAGE_SIZE = 5

export function PostsPage() {
  const auth = useAuth()
  const [page, setPage] = useState(0)
  const [data, setData] = useState<PageResponse<PostDTO> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError>()
  const requestRef = useRef(0)
  const mountedRef = useRef(false)
  const pageRef = useRef(page)
  pageRef.current = page

  const load = useCallback(async (pageNumber: number) => {
    if (!mountedRef.current) return
    const requestId = ++requestRef.current
    setLoading(true)
    setError(undefined)
    let movingBack = false
    try {
      const response = await getPosts({ page: pageNumber, size: POSTS_PAGE_SIZE, sort: 'id,desc' })
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
        setError(normalizeApiError(cause))
        setData(null)
      }
    } finally {
      if (requestRef.current === requestId && !movingBack) setLoading(false)
    }
  }, [])

  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false }
  }, [])

  useEffect(() => {
    void load(page)
    return () => { requestRef.current += 1 }
  }, [load, page])

  const onCreated = (_post: PostDTO) => {
    if (pageRef.current === 0) {
      void load(0)
    } else {
      setPage(0)
    }
  }

  const onUpdated = (updated: PostDTO) => {
    setData((current) => current ? { ...current, content: current.content.map((post) => post.id === updated.id ? updated : post) } : current)
  }

  const onDeleted = () => {
    void load(pageRef.current)
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-3xl font-bold">Posts</h1>
        <p className="mt-2 text-lb-muted">Community posts and updates</p>
      </div>

      {auth.isAuthenticated && <PostComposer onCreated={onCreated} />}

      {loading && (
        <div className="flex min-h-48 items-center justify-center" role="status" aria-label="Loading posts"><Spinner /></div>
      )}

      {!loading && error && (
        <div className="space-y-3">
          <Alert tone="error" title={error.status === 403 ? 'Forbidden' : error.status === 404 ? 'Posts not found' : 'Unable to load posts'}>{error.message}</Alert>
          <Button variant="secondary" onClick={() => void load(page)}>Retry</Button>
        </div>
      )}

      {!loading && !error && data?.content.length === 0 && (
        <EmptyState title="No posts yet" description="Be the first to share something with the community." />
      )}

      {!loading && !error && data && data.content.length > 0 && (
        <>
          <div className="grid gap-4">
            {data.content.map((post) => (
              <PostCard key={post.id} post={post} onUpdated={onUpdated} onDeleted={onDeleted} />
            ))}
          </div>
          <div className="mt-6">
            <Pagination currentPage={page} totalPages={data.totalPages} onPageChange={setPage} />
          </div>
        </>
      )}
    </div>
  )
}
