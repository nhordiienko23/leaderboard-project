import { useEffect, useRef, useState } from 'react'
import { Trophy } from 'lucide-react'
import { normalizeApiError } from '../../api/errors'
import { getTop10, getTop100 } from '../../api/leaderboard'
import type { LeaderboardEntryDTO } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { Alert, Button, EmptyState, Spinner } from '../../components/ui'
import { LeaderboardResults } from '../../features/leaderboard/LeaderboardResults'
import { sameRankings } from '../../features/leaderboard/RankingTransitions'

type LeaderboardSize = 10 | 100
const POLL_INTERVAL = 5_000

function normalizeUserId(value: unknown): number | null {
  const userId = typeof value === 'number'
    ? value
    : typeof value === 'string' && /^\d+$/.test(value)
      ? Number(value)
      : Number.NaN

  return Number.isSafeInteger(userId) && userId > 0 ? userId : null
}

export function LeaderboardPage() {
  const auth = useAuth()
  const [size, setSize] = useState<LeaderboardSize>(10)
  const [entries, setEntries] = useState<LeaderboardEntryDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string>()
  const [refreshWarning, setRefreshWarning] = useState<string>()
  const [requestInFlight, setRequestInFlight] = useState(false)
  const [animateChanges, setAnimateChanges] = useState(false)
  const requestRef = useRef(0)
  const requestNowRef = useRef<(() => void) | null>(null)
  const currentUserId = auth.isAuthenticated ? normalizeUserId(auth.claims?.sub) : null

  useEffect(() => {
    const requestId = ++requestRef.current
    let active = true
    let inFlight = false
    let initialAttemptComplete = false
    let hasSuccessfulResponse = false
    let previousResponseSucceeded = false
    let timer: ReturnType<typeof setTimeout> | undefined

    const isCurrent = () => active && requestRef.current === requestId
    const clearTimer = () => {
      if (timer !== undefined) {
        clearTimeout(timer)
        timer = undefined
      }
    }

    const load = async (manualRetry = false) => {
      if (!isCurrent() || inFlight) return
      clearTimer()
      inFlight = true
      setRequestInFlight(true)
      const foreground = manualRetry || !initialAttemptComplete
      if (foreground) {
        setLoading(true)
        setError(undefined)
        setRefreshWarning(undefined)
      }

      try {
        const response = size === 10 ? await getTop10() : await getTop100()
        if (isCurrent()) {
          setAnimateChanges(!foreground && previousResponseSucceeded)
          previousResponseSucceeded = true
          hasSuccessfulResponse = true
          setEntries((previous) => sameRankings(previous, response.data) ? previous : response.data)
          setError(undefined)
          setRefreshWarning(undefined)
        }
      } catch (cause) {
        if (isCurrent()) {
          previousResponseSucceeded = false
          setAnimateChanges(false)
          if (hasSuccessfulResponse) {
            setRefreshWarning('Unable to refresh the leaderboard. Showing the last successfully loaded results.')
          } else {
            const apiError = normalizeApiError(cause)
            setError(apiError.isNetworkError
              ? apiError.message
              : 'The leaderboard could not be loaded. Please try again.')
            setEntries([])
          }
        }
      } finally {
        inFlight = false
        initialAttemptComplete = true
        if (isCurrent()) {
          setRequestInFlight(false)
          if (foreground) setLoading(false)
          if (document.visibilityState === 'visible') {
            timer = setTimeout(() => { void load() }, POLL_INTERVAL)
          }
        }
      }
    }

    const handleVisibilityChange = () => {
      if (document.visibilityState !== 'visible') {
        clearTimer()
      } else {
        clearTimer()
        void load()
      }
    }

    requestNowRef.current = () => { void load(true) }
    document.addEventListener('visibilitychange', handleVisibilityChange)
    queueMicrotask(() => {
      if (isCurrent()) void load()
    })

    return () => {
      active = false
      clearTimer()
      document.removeEventListener('visibilitychange', handleVisibilityChange)
      requestNowRef.current = null
      if (requestRef.current === requestId) requestRef.current += 1
    }
  }, [size])

  function selectSize(nextSize: LeaderboardSize) {
    if (nextSize === size) return
    requestRef.current += 1
    setLoading(true)
    setError(undefined)
    setRefreshWarning(undefined)
    setSize(nextSize)
  }

  function retryLoad() {
    requestNowRef.current?.()
  }

  return (
    <section>
      <header className="mb-7 flex items-start gap-3">
        <span
          role="img"
          aria-label="Leaderboard trophy"
          className="mt-1 rounded-lg border border-amber-400/20 bg-amber-400/5 p-2 text-amber-300"
        >
          <Trophy className="h-5 w-5" aria-hidden="true" />
        </span>
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-lb-text">Leaderboard</h1>
          <p className="mt-2 text-lb-muted">See how the community ranks by points.</p>
        </div>
      </header>

      <div className="mb-5 inline-flex rounded-xl border border-lb-border bg-lb-elevated p-1" role="group" aria-label="Leaderboard size">
        <Button
          variant={size === 10 ? 'primary' : 'ghost'}
          aria-pressed={size === 10}
          className="min-h-9 rounded-lg px-4 py-1.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-lb-focus"
          onClick={() => selectSize(10)}
        >
          Top 10
        </Button>
        <Button
          variant={size === 100 ? 'primary' : 'ghost'}
          aria-pressed={size === 100}
          className="min-h-9 rounded-lg px-4 py-1.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-lb-focus"
          onClick={() => selectSize(100)}
        >
          Top 100
        </Button>
      </div>

      {loading && (
        <div className="flex min-h-48 items-center justify-center">
          <Spinner label="Loading leaderboard" />
        </div>
      )}

      {!loading && error && (
        <div className="space-y-3">
          <Alert tone="error" title="Unable to load leaderboard">{error}</Alert>
          <Button variant="secondary" onClick={retryLoad} disabled={requestInFlight}>Retry</Button>
        </div>
      )}

      {!loading && refreshWarning && (
        <div className="mb-4">
          <Alert tone="warning" title="Leaderboard refresh delayed">{refreshWarning}</Alert>
        </div>
      )}

      {!loading && !error && entries.length === 0 && (
        <EmptyState
          title="No leaderboard entries yet"
          description="There are no ranked users to display right now."
        />
      )}

      {!loading && !error && entries.length > 0 && (
        <LeaderboardResults entries={entries} showPodium currentUserId={currentUserId} animateChanges={animateChanges} />
      )}
    </section>
  )
}
