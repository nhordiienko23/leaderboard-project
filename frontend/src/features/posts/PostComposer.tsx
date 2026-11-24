import { useState, type FormEvent } from 'react'
import { createPost } from '../../api/posts'
import { normalizeApiError, type ApiError } from '../../api/errors'
import type { PostDTO } from '../../api/types'
import { Alert, Button, Card } from '../../components/ui'
import { httpErrorTitle } from './httpErrorTitle'

interface PostComposerProps {
  onCreated: (post: PostDTO) => void
}

export function PostComposer({ onCreated }: PostComposerProps) {
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
      setFieldError('content cannot be empty')
      return
    }
    if (content.length > 255) {
      setFieldError('content must not exceed 255 characters')
      return
    }

    setPending(true)
    try {
      const response = await createPost({ content })
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
    <Card className="mb-6 p-4 sm:p-5">
      <form onSubmit={submit} noValidate>
        <label htmlFor="post-content" className="mb-2 block text-sm font-medium text-lb-text">Create a post</label>
        <textarea
          id="post-content"
          value={content}
          onChange={(event) => setContent(event.target.value)}
          maxLength={255}
          rows={4}
          disabled={pending}
          aria-invalid={Boolean(fieldError) || undefined}
          aria-describedby="post-content-help"
          placeholder="Share something with the community..."
          className="w-full resize-y rounded-lg border border-lb-border bg-lb-background px-3 py-3 text-lb-text placeholder:text-lb-subtle disabled:opacity-70"
        />
        <div className="mt-2 flex items-start justify-between gap-4">
          <p id="post-content-help" className={fieldError ? 'text-sm text-lb-error' : 'text-sm text-lb-muted'}>
            {fieldError ?? `${content.length}/255 characters`}
          </p>
          <Button type="submit" loading={pending} disabled={!content.trim()}>Post</Button>
        </div>
        {error && <div className="mt-3"><Alert tone="error" title={httpErrorTitle(error)}>{error.message}</Alert></div>}
      </form>
    </Card>
  )
}
