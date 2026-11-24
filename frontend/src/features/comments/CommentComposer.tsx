import { useState, type FormEvent } from 'react'
import { createComment } from '../../api/comments'
import { normalizeApiError, type ApiError } from '../../api/errors'
import type { CommentDTO } from '../../api/types'
import { Alert, Button, Card } from '../../components/ui'
import { httpErrorTitle } from '../posts/httpErrorTitle'

interface CommentComposerProps {
  postId: number
  onCreated: (comment: CommentDTO) => void
}

export function CommentComposer({ postId, onCreated }: CommentComposerProps) {
  const [content, setContent] = useState('')
  const [pending, setPending] = useState(false)
  const [fieldError, setFieldError] = useState<string>()
  const [error, setError] = useState<ApiError>()

  const submit = async (event: FormEvent) => {
    event.preventDefault()
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
      const response = await createComment(postId, { content })
      onCreated(response.data)
      setContent('')
    } catch (cause) {
      const apiError = normalizeApiError(cause)
      setFieldError(apiError.fieldErrors.content)
      setError(apiError.fieldErrors.content && !httpErrorTitle(apiError) ? undefined : apiError)
    } finally {
      setPending(false)
    }
  }

  return (
    <Card className="p-4 sm:p-5">
      <form onSubmit={submit} noValidate>
        <label htmlFor="comment-content" className="mb-2 block text-sm font-medium text-lb-text">Create a comment</label>
        <textarea
          id="comment-content"
          value={content}
          onChange={(event) => setContent(event.target.value)}
          maxLength={1000}
          rows={4}
          disabled={pending}
          aria-invalid={Boolean(fieldError) || undefined}
          aria-describedby="comment-content-help"
          placeholder="Write a comment..."
          className="w-full resize-y rounded-lg border border-lb-border bg-lb-background px-3 py-3 text-lb-text placeholder:text-lb-subtle disabled:opacity-70"
        />
        <div className="mt-2 flex items-start justify-between gap-4">
          <p id="comment-content-help" className={fieldError ? 'text-sm text-lb-error' : 'text-sm text-lb-muted'}>
            {fieldError ?? `${content.length}/1000 characters`}
          </p>
          <Button type="submit" loading={pending} disabled={!content.trim()}>Comment</Button>
        </div>
        {error && <div className="mt-3"><Alert tone="error" title={httpErrorTitle(error)}>{error.message}</Alert></div>}
      </form>
    </Card>
  )
}
