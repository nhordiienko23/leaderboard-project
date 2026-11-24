import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Pencil, Trash2 } from 'lucide-react'
import { useAuth } from '../../auth/AuthContext'
import { deletePost, updatePost } from '../../api/posts'
import { normalizeApiError, type ApiError } from '../../api/errors'
import type { PostDTO } from '../../api/types'
import { Alert, Button, Dialog } from '../../components/ui'
import { LikeActions } from '../likes/LikeActions'
import { httpErrorTitle } from './httpErrorTitle'
import { UserName } from '../users/UserName'

interface PostCardProps {
  post: PostDTO
  onUpdated?: (post: PostDTO) => void
  onDeleted?: (id: number) => void
  engagementRefreshKey?: number
}

function formatTimestamp(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

export function PostCard({ post, onUpdated, onDeleted, engagementRefreshKey }: PostCardProps) {
  const auth = useAuth()
  const [editOpen, setEditOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [content, setContent] = useState(post.content)
  const [pending, setPending] = useState(false)
  const [fieldError, setFieldError] = useState<string>()
  const [error, setError] = useState<ApiError>()

  const canMutate = auth.isAuthenticated && (
    auth.claims?.role === 'ADMIN' || auth.claims?.sub === post.userId
  )

  const save = async () => {
    if (pending) return
    setFieldError(undefined)
    setError(undefined)
    if (!content.trim()) {
      setFieldError('content cannot be empty')
      return
    }
    if (content.length > 255) {
      setFieldError('content must not exceed 255 characters')
      return
    }

    setPending(true)
    try {
      const response = await updatePost(post.id, { content })
      onUpdated?.(response.data)
      setEditOpen(false)
    } catch (cause) {
      const apiError = normalizeApiError(cause)
      setFieldError(apiError.fieldErrors.content)
      setError(apiError.fieldErrors.content && !httpErrorTitle(apiError) ? undefined : apiError)
    } finally {
      setPending(false)
    }
  }

  const remove = async () => {
    if (pending) return
    setError(undefined)
    setPending(true)
    try {
      await deletePost(post.id)
      setDeleteOpen(false)
      onDeleted?.(post.id)
    } catch (cause) {
      setError(normalizeApiError(cause))
    } finally {
      setPending(false)
    }
  }

  return (
    <>
      <article className="rounded-xl border border-lb-border bg-lb-surface p-5 shadow-sm">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-xs font-medium text-lb-muted"><UserName userId={post.userId} /></p>
            <Link to={`/posts/${post.id}`} className="mt-2 block whitespace-pre-wrap wrap-break-word text-base text-lb-text hover:text-lb-primary-hover">
              {post.content}
            </Link>
          </div>
          {canMutate && (
            <div className="flex shrink-0 gap-1">
              <Button variant="ghost" icon={<Pencil size={16} aria-hidden="true" />} aria-label={`Edit post ${post.id}`} onClick={() => { setContent(post.content); setError(undefined); setFieldError(undefined); setEditOpen(true) }}>Edit</Button>
              <Button variant="ghost" icon={<Trash2 size={16} aria-hidden="true" />} aria-label={`Delete post ${post.id}`} onClick={() => { setError(undefined); setDeleteOpen(true) }}>Delete</Button>
            </div>
          )}
        </div>
        <div className="mt-4 flex flex-wrap gap-x-4 gap-y-1 text-xs text-lb-subtle">
          <time dateTime={post.createdAt}>Created {formatTimestamp(post.createdAt)}</time>
          <time dateTime={post.updatedAt}>Updated {formatTimestamp(post.updatedAt)}</time>
        </div>
        <LikeActions postId={post.id} refreshKey={engagementRefreshKey} />
      </article>

      <Dialog open={editOpen} title="Edit post" description="Update the post content." onClose={() => !pending && setEditOpen(false)}>
        <div>
          <label htmlFor={`edit-post-${post.id}`} className="mb-2 block text-sm font-medium">Post content</label>
          <textarea
            id={`edit-post-${post.id}`}
            value={content}
            maxLength={255}
            rows={5}
            disabled={pending}
            onChange={(event) => setContent(event.target.value)}
            aria-invalid={Boolean(fieldError) || undefined}
            className="w-full rounded-lg border border-lb-border bg-lb-background px-3 py-3"
          />
          <p className={fieldError ? 'mt-2 text-sm text-lb-error' : 'mt-2 text-sm text-lb-muted'}>
            {fieldError ?? `${content.length}/255 characters`}
          </p>
          {error && <div className="mt-3"><Alert tone="error" title={httpErrorTitle(error)}>{error.message}</Alert></div>}
          <div className="mt-5 flex justify-end gap-2">
            <Button variant="secondary" disabled={pending} onClick={() => setEditOpen(false)}>Cancel</Button>
            <Button loading={pending} disabled={!content.trim()} onClick={save}>Save changes</Button>
          </div>
        </div>
      </Dialog>

      <Dialog open={deleteOpen} title="Delete post" description="This action cannot be undone." onClose={() => !pending && setDeleteOpen(false)}>
        {error && <div className="mb-4"><Alert tone="error" title={httpErrorTitle(error)}>{error.message}</Alert></div>}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" disabled={pending} onClick={() => setDeleteOpen(false)}>Cancel</Button>
          <Button variant="danger" loading={pending} onClick={remove}>Delete post</Button>
        </div>
      </Dialog>
    </>
  )
}