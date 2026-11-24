import { useState } from 'react'
import { Pencil, Trash2 } from 'lucide-react'
import { useAuth } from '../../auth/AuthContext'
import { deleteComment, updateComment } from '../../api/comments'
import { normalizeApiError, type ApiError } from '../../api/errors'
import type { CommentDTO } from '../../api/types'
import { Alert, Button, Dialog } from '../../components/ui'
import { LikeActions } from '../likes/LikeActions'
import { httpErrorTitle } from '../posts/httpErrorTitle'
import { UserName } from '../users/UserName'

interface CommentItemProps {
  comment: CommentDTO
  onUpdated: (comment: CommentDTO) => void
  onDeleted: (id: number) => void
}

function formatTimestamp(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

export function CommentItem({ comment, onUpdated, onDeleted }: CommentItemProps) {
  const auth = useAuth()
  const [editOpen, setEditOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [content, setContent] = useState(comment.content)
  const [pending, setPending] = useState(false)
  const [fieldError, setFieldError] = useState<string>()
  const [error, setError] = useState<ApiError>()

  const canMutate = auth.isAuthenticated && (
    auth.claims?.role === 'ADMIN' || auth.claims?.sub === comment.userId
  )

  const save = async () => {
    if (pending) return
    setFieldError(undefined)
    setError(undefined)
    if (!content.trim()) {
      setFieldError('Comment content must not be blank')
      return
    }
    if (content.length > 1000) {
      setFieldError('Comment content must not exceed 1000 characters')
      return
    }

    setPending(true)
    try {
      const response = await updateComment(comment.postId, comment.id, { content })
      onUpdated(response.data)
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
      await deleteComment(comment.postId, comment.id)
      setDeleteOpen(false)
      onDeleted(comment.id)
    } catch (cause) {
      setError(normalizeApiError(cause))
    } finally {
      setPending(false)
    }
  }

  return (
    <>
      <article className="min-w-0 rounded-xl border border-lb-border bg-lb-surface p-4">
        <div className="flex min-w-0 flex-col items-start justify-between gap-4 sm:flex-row">
          <div className="min-w-0 w-full flex-1">
            <p className="text-xs font-medium text-lb-muted"><UserName userId={comment.userId} /></p>
            <p className="mt-2 whitespace-break-spaces wrap-anywhere text-sm text-lb-text">{comment.content}</p>
          </div>
          {canMutate && (
            <div className="flex max-w-full flex-wrap gap-1">
              <Button variant="ghost" icon={<Pencil size={16} aria-hidden="true" />} aria-label={`Edit comment ${comment.id}`} onClick={() => { setContent(comment.content); setFieldError(undefined); setError(undefined); setEditOpen(true) }}>Edit</Button>
              <Button variant="ghost" icon={<Trash2 size={16} aria-hidden="true" />} aria-label={`Delete comment ${comment.id}`} onClick={() => { setError(undefined); setDeleteOpen(true) }}>Delete</Button>
            </div>
          )}
        </div>
        <time className="mt-3 block text-xs text-lb-subtle" dateTime={comment.createdAt}>Created {formatTimestamp(comment.createdAt)}</time>
        <LikeActions postId={comment.postId} commentId={comment.id} />
      </article>

      <Dialog open={editOpen} title="Edit comment" description="Update the comment content." onClose={() => !pending && setEditOpen(false)}>
        <div>
          <label htmlFor={`edit-comment-${comment.id}`} className="mb-2 block text-sm font-medium">Comment content</label>
          <textarea
            id={`edit-comment-${comment.id}`}
            value={content}
            maxLength={1000}
            rows={5}
            disabled={pending}
            onChange={(event) => setContent(event.target.value)}
            aria-invalid={Boolean(fieldError) || undefined}
            className="w-full rounded-lg border border-lb-border bg-lb-background px-3 py-3"
          />
          <p className={fieldError ? 'mt-2 text-sm text-lb-error' : 'mt-2 text-sm text-lb-muted'}>
            {fieldError ?? `${content.length}/1000 characters`}
          </p>
          {error && <div className="mt-3"><Alert tone="error" title={httpErrorTitle(error)}>{error.message}</Alert></div>}
          <div className="mt-5 flex flex-wrap justify-end gap-2">
            <Button variant="secondary" disabled={pending} onClick={() => setEditOpen(false)}>Cancel</Button>
            <Button loading={pending} disabled={!content.trim()} onClick={() => void save()}>Save changes</Button>
          </div>
        </div>
      </Dialog>

      <Dialog open={deleteOpen} title="Delete comment" description="This action cannot be undone." onClose={() => !pending && setDeleteOpen(false)}>
        {error && <div className="mb-4"><Alert tone="error" title={httpErrorTitle(error)}>{error.message}</Alert></div>}
        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="secondary" disabled={pending} onClick={() => setDeleteOpen(false)}>Cancel</Button>
          <Button variant="danger" loading={pending} onClick={() => void remove()}>Delete comment</Button>
        </div>
      </Dialog>
    </>
  )
}
