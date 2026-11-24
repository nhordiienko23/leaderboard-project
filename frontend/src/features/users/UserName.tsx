import { useEffect, useSyncExternalStore } from 'react'
import { Link } from 'react-router-dom'
import { getUserSummaryState, loadUserSummary, subscribeUserSummary } from '../../api/userSummaries'

interface UserNameProps {
  userId: number
  profileLink?: boolean
  retryOnError?: boolean
}

export function UserName({ userId, profileLink = false, retryOnError = true }: UserNameProps) {
  const state = useSyncExternalStore(
    (listener) => subscribeUserSummary(userId, listener),
    () => getUserSummaryState(userId),
  )

  useEffect(() => {
    void loadUserSummary(userId)
  }, [userId])

  if (state.error) {
    if (state.error.status === 404) return <span>Deleted user</span>
    return (
      <span role="status" title={state.error.message}>
        Username unavailable.{' '}
        {retryOnError && (
          <button type="button" className="underline" title={state.error.message}
            onClick={() => void loadUserSummary(userId)}>Retry username</button>
        )}
      </span>
    )
  }

  if (!state.user) return <span>Loading username...</span>

  return profileLink ? (
    <Link to={`/users/${userId}`} aria-label={`View profile for ${state.user.username}`}
      className="font-medium text-lb-text underline-offset-4 hover:text-lb-focus hover:underline">
      {state.user.username}
    </Link>
  ) : <span>{state.user.username}</span>
}
