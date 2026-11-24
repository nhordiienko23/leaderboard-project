import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { MessageCircle, ThumbsDown, ThumbsUp } from 'lucide-react'
import { useAuth } from '../../auth/AuthContext'
import { getCommentEngagement } from '../../api/comments'
import { normalizeApiError, type ApiError } from '../../api/errors'
import { likeComment, likePost, unlikeComment, unlikePost } from '../../api/likes'
import { getPostEngagement } from '../../api/posts'
import type { CommentEngagementResponse, PostEngagementResponse } from '../../api/types'
import { Alert, Button, Spinner } from '../../components/ui'
import { httpErrorTitle } from '../posts/httpErrorTitle'

interface LikeActionsProps {
  postId: number
  commentId?: number
  refreshKey?: number
}

type Engagement = PostEngagementResponse | CommentEngagementResponse
type PendingAction = 'like' | 'unlike'
interface TaggedValue<T> {
  key: string
  value: T
}
interface EngagementState {
  key: string
  summary?: Engagement
  loading: boolean
  error?: ApiError
}

export function LikeActions({ postId, commentId, refreshKey = 0 }: LikeActionsProps) {
  const auth = useAuth()
  const viewer = auth.status === 'restoring'
    ? 'restoring'
    : auth.isAuthenticated
      ? `user:${auth.claims?.sub}`
      : 'anonymous'
  const resourceKey = `${postId}:${commentId ?? 'post'}:${viewer}`
  const requestRef = useRef(0)
  const resourceKeyRef = useRef(resourceKey)
  resourceKeyRef.current = resourceKey
  const [state, setState] = useState<EngagementState>({ key: resourceKey, loading: true })
  const [pending, setPending] = useState<TaggedValue<PendingAction>>()
  const [feedback, setFeedback] = useState<TaggedValue<{
    tone: 'success' | 'warning' | 'error'
    message: string
    title?: string
  }>>()
  const [fadingFeedback, setFadingFeedback] = useState<typeof feedback>()

  const isCurrentState = state.key === resourceKey
  const summary = isCurrentState ? state.summary : undefined
  const postSummary = commentId === undefined
    ? summary as PostEngagementResponse | undefined
    : undefined
  const loading = auth.status === 'restoring' || (isCurrentState ? state.loading : true)
  const error = isCurrentState ? state.error : undefined
  const activePending = pending?.key === resourceKey ? pending.value : undefined

  const loadSummary = useCallback(async () => {
    if (auth.status === 'restoring') return

    const requestId = ++requestRef.current
    const expectedKey = resourceKey
    setState((current) => ({
      key: expectedKey,
      summary: current.key === expectedKey ? current.summary : undefined,
      loading: true,
    }))
    try {
      const response = commentId === undefined
        ? await getPostEngagement(postId)
        : await getCommentEngagement(postId, commentId)
      if (requestRef.current === requestId && resourceKeyRef.current === expectedKey) {
        setState({ key: expectedKey, summary: response.data, loading: false })
      }
    } catch (cause) {
      if (requestRef.current === requestId && resourceKeyRef.current === expectedKey) {
        setState({ key: expectedKey, loading: false, error: normalizeApiError(cause) })
      }
    }
  }, [auth.status, commentId, postId, resourceKey, refreshKey])

  useEffect(() => {
    void loadSummary()
    return () => { requestRef.current += 1 }
  }, [loadSummary])

  useEffect(() => {
    setPending(undefined)
    setFeedback(undefined)
  }, [resourceKey])

  useEffect(() => {
    if (!feedback || feedback.key !== resourceKey || feedback.value.tone === 'error') return

    const fadeTimer = window.setTimeout(() => setFadingFeedback(feedback), 3600)
    const dismissTimer = window.setTimeout(() => {
      setFeedback((current) => current === feedback ? undefined : current)
    }, 4000)
    return () => {
      window.clearTimeout(fadeTimer)
      window.clearTimeout(dismissTimer)
    }
  }, [feedback, resourceKey])

  const label = commentId === undefined ? `post ${postId}` : `comment ${commentId}`
  const likedByMe = summary?.likedByMe
  const canMutate = auth.isAuthenticated && !loading && !error && summary !== undefined &&
    likedByMe !== null && activePending === undefined

  const mutate = async (action: PendingAction) => {
    if (!canMutate || (action === 'like' && likedByMe !== false) || (action === 'unlike' && likedByMe !== true)) return

    const actionKey = resourceKey
    let shouldRefresh = false
    setFeedback(undefined)
    setPending({ key: actionKey, value: action })
    try {
      if (action === 'like') {
        if (commentId === undefined) await likePost(postId)
        else await likeComment(postId, commentId)
      } else if (commentId === undefined) {
        await unlikePost(postId)
      } else {
        await unlikeComment(postId, commentId)
      }
      if (resourceKeyRef.current === actionKey) {
        shouldRefresh = true
        setFeedback({ key: actionKey, value: { tone: 'success', message: action === 'like' ? 'Like confirmed.' : 'Like removed.' } })
      }
    } catch (cause) {
      const apiError = normalizeApiError(cause)
      if (resourceKeyRef.current === actionKey) {
        if (action === 'like' && apiError.status === 409) {
          shouldRefresh = true
          setFeedback({ key: actionKey, value: { tone: 'warning', message: 'This item is already liked.' } })
        } else if (action === 'unlike' && apiError.status === 404 && apiError.message === 'Like not found') {
          shouldRefresh = true
          setFeedback({ key: actionKey, value: { tone: 'warning', message: 'No existing like was found.' } })
        } else {
          setFeedback({
            key: actionKey,
            value: { tone: 'error', message: apiError.message, title: httpErrorTitle(apiError) },
          })
        }
      }
    } finally {
      if (resourceKeyRef.current === actionKey) {
        if (shouldRefresh) await loadSummary()
        setPending((current) => current?.key === actionKey ? undefined : current)
      }
    }
  }

  const activeFeedback = feedback?.key === resourceKey ? feedback.value : undefined

  return (
    <div className="mt-4">
      <div className="flex flex-wrap items-center gap-2">
        {loading && (
          <span className="inline-flex items-center gap-2 text-sm text-lb-muted" role="status" aria-label={`Loading engagement for ${label}`}>
            <Spinner /> Loading engagement
          </span>
        )}

        {auth.isAuthenticated ? (
          <>
            <Button
              variant="secondary"
              loading={activePending === 'like'}
              disabled={!canMutate || likedByMe !== false}
              onClick={() => void mutate('like')}
              aria-label={`Like ${label}`}
              aria-pressed={typeof likedByMe === 'boolean' ? likedByMe : undefined}
              icon={<ThumbsUp size={16} aria-hidden="true" />}
              className={likedByMe === true ? 'border-[#8B5CF6] bg-[#7C3AED]/20 text-[#DDD6FE]' : ''}
            >
              {summary ? summary.likeCount : 'Like'}
            </Button>
            <Button
              variant="ghost"
              loading={activePending === 'unlike'}
              disabled={!canMutate || likedByMe !== true}
              onClick={() => void mutate('unlike')}
              aria-label={`Unlike ${label}`}
              icon={<ThumbsDown size={16} aria-hidden="true" />}
            >
              Unlike
            </Button>
          </>
        ) : !loading && summary ? (
            <span className="inline-flex items-center gap-2 text-sm text-lb-muted" aria-label={`${summary.likeCount} likes for ${label}`}>
              <ThumbsUp size={16} aria-hidden="true" /> {summary.likeCount}
            </span>
          ) : null}

        {!loading && postSummary && (
          <Link
            to={`/posts/${postId}#comments`}
            className="inline-flex min-h-10 items-center gap-2 rounded-lg px-3 text-sm text-lb-muted hover:bg-lb-background hover:text-lb-text"
            aria-label={`${postSummary.commentCount} comments on post ${postId}`}
          >
            <MessageCircle size={16} aria-hidden="true" />
            {postSummary.commentCount}
          </Link>
        )}
      </div>

      {error && (
        <div className="mt-3 space-y-3">
          <Alert tone="error" title="Unable to load engagement">{error.message}</Alert>
          <Button variant="secondary" onClick={() => void loadSummary()} aria-label={`Retry engagement for ${label}`}>Retry</Button>
        </div>
      )}

      {activeFeedback && (
        <div className={`mt-3 ${feedback === fadingFeedback ? 'motion-safe:transition-opacity motion-safe:duration-[400ms] motion-safe:opacity-0' : ''}`}>
          <Alert tone={activeFeedback.tone} title={activeFeedback.title}>{activeFeedback.message}</Alert>
        </div>
      )}
    </div>
  )
}
