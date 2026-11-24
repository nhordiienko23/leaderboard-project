import { useEffect, useState } from 'react'
import { getProfileActivity } from '../../api/posts'
import { normalizeApiError, type ApiError } from '../../api/errors'
import type { ProfileActivityResponse } from '../../api/types'
import { Alert, Button, Spinner } from '../../components/ui'

const metrics: { key: keyof ProfileActivityResponse; label: string }[] = [
  { key: 'postsCreated', label: 'Posts' },
  { key: 'commentsAuthored', label: 'Comments' },
  { key: 'postLikesGiven', label: 'Post likes given' },
  { key: 'commentLikesGiven', label: 'Comment likes given' },
  { key: 'postLikesReceived', label: 'Post likes received' },
  { key: 'commentLikesReceived', label: 'Comment likes received' },
]

export function ProfileActivity({ userId }: { userId: number }) {
  const [activity, setActivity] = useState<ProfileActivityResponse>()
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError>()
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    let active = true
    setActivity(undefined)
    setError(undefined)
    setLoading(true)
    void getProfileActivity(userId).then((response) => {
      if (active) setActivity(response.data)
    }).catch((cause) => {
      if (active) setError(normalizeApiError(cause))
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => { active = false }
  }, [userId, retry])

  const visible = metrics.filter(({ key }) => activity && activity[key] > 0)
  if (!loading && !error && visible.length === 0) return null

  return (
    <section className="mt-7 border-t border-lb-border pt-6" aria-label="Profile activity">
      <h2 className="text-lg font-semibold">Activity</h2>
      {loading && <div className="mt-3 flex items-center gap-2 text-sm text-lb-muted" role="status" aria-label="Loading activity"><Spinner /> Loading activity</div>}
      {error && <div className="mt-3 space-y-3"><Alert tone="error" title="Unable to load activity">{error.message}</Alert><Button variant="secondary" onClick={() => setRetry((value) => value + 1)}>Retry activity</Button></div>}
      {!loading && !error && activity && (
        <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          {visible.map(({ key, label }) => <div key={key}><dt className="text-sm text-lb-muted">{label}</dt><dd className="mt-1 font-medium">{activity[key]}</dd></div>)}
        </dl>
      )}
    </section>
  )
}
