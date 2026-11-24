import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { getPost } from '../../api/posts'
import { normalizeApiError } from '../../api/errors'
import type { PostDTO } from '../../api/types'
import { PostCard } from '../../features/posts/PostCard'
import { Alert, Button, Spinner } from '../../components/ui'
import { CommentList } from '../../features/comments/CommentList'

function formatTimestamp(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

export function PostDetailPage() {
  const { postId } = useParams()
  const navigate = useNavigate()
  const [post, setPost] = useState<PostDTO | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [notFound, setNotFound] = useState(false)
  const [forbidden, setForbidden] = useState(false)
  const [retryKey, setRetryKey] = useState(0)
  const [engagementRefreshKey, setEngagementRefreshKey] = useState(0)

  useEffect(() => {
    let obsolete = false
    setPost(null)
    setError(undefined)
    setNotFound(false)
    setForbidden(false)

    if (!postId || !/^\d+$/.test(postId)) {
      setError('Invalid post ID.')
      setLoading(false)
      return
    }

    const id = Number(postId)
    if (!Number.isSafeInteger(id) || id <= 0) {
      setError('Invalid post ID.')
      setLoading(false)
      return
    }

    setLoading(true)
    void getPost(id).then((response) => {
      if (!obsolete) setPost(response.data)
    }).catch((cause) => {
      if (obsolete) return
      const apiError = normalizeApiError(cause)
      if (apiError.status === 404) setNotFound(true)
      else if (apiError.status === 403) setForbidden(true)
      else setError(apiError.message)
    }).finally(() => {
      if (!obsolete) setLoading(false)
    })

    return () => { obsolete = true }
  }, [postId, retryKey])

  const onDeleted = () => navigate('/posts', { replace: true })

  return (
    <div className="mx-auto max-w-3xl">
      <Link to="/posts" className="mb-5 inline-flex items-center gap-2 text-sm text-lb-muted hover:text-lb-text"><ArrowLeft size={16} aria-hidden="true" />Back to posts</Link>

      {loading && <div className="flex min-h-48 items-center justify-center" role="status" aria-label="Loading post"><Spinner /></div>}

      {!loading && notFound && (
        <Alert tone="warning" title="Post not found">This post does not exist or is no longer available.</Alert>
      )}

      {!loading && forbidden && (
        <Alert tone="error" title="Forbidden">You do not have permission to view this post.</Alert>
      )}

      {!loading && error && (
        <div className="space-y-3"><Alert tone="error" title="Unable to load post">{error}</Alert><Button variant="secondary" onClick={() => setRetryKey((value) => value + 1)}>Retry</Button></div>
      )}

      {!loading && post && (
        <>
          <h1 className="mb-5 text-3xl font-bold">Post #{post.id}</h1>
          <PostCard
            post={post}
            onUpdated={setPost}
            onDeleted={onDeleted}
            engagementRefreshKey={engagementRefreshKey}
          />
          <div className="mt-3 text-xs text-lb-subtle">
            <time dateTime={post.createdAt}>Created {formatTimestamp(post.createdAt)}</time>
            {' · '}
            <time dateTime={post.updatedAt}>Updated {formatTimestamp(post.updatedAt)}</time>
          </div>
          <CommentList
            key={post.id}
            postId={post.id}
            onEngagementChanged={() => setEngagementRefreshKey((current) => current + 1)}
          />
        </>
      )}
    </div>
  )
}