import { Profiler, StrictMode } from 'react'
import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { act, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as leaderboardApi from '../../api/leaderboard'
import { apiClient } from '../../api/client'
import type { LeaderboardEntryDTO } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { LeaderboardPage } from './LeaderboardPage'

vi.mock('../../auth/AuthContext', () => ({ useAuth: vi.fn() }))

type Response = AxiosResponse<LeaderboardEntryDTO[]>

let nextUserId = 200000
const summaries = new Map<number, string>()

function response(username: string): Response {
  const userId = nextUserId++
  summaries.set(userId, username)
  return {
    data: [{ userId, username: `Projection ${username}`, points: 10 }],
    status: 200,
    statusText: 'OK',
    headers: {},
    config: { headers: new AxiosHeaders() },
  }
}

function emptyResponse(): Response {
  return { ...response('unused'), data: [] }
}

function entriesResponse(entries: LeaderboardEntryDTO[]): Response {
  entries.forEach((entry) => summaries.set(entry.userId, entry.username))
  return {
    data: entries,
    status: 200,
    statusText: 'OK',
    headers: {},
    config: { headers: new AxiosHeaders() },
  }
}

function namedEntries(names: string[]): LeaderboardEntryDTO[] {
  return names.map((username, index) => ({
    userId: nextUserId++,
    username,
    points: 100 - index,
  }))
}

function deferred() {
  let resolve!: (value: Response) => void
  let reject!: (reason: Error) => void
  const promise = new Promise<Response>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

async function flushPromises() {
  await act(async () => {
    await Promise.resolve()
    await Promise.resolve()
  })
}

async function advanceTimersBy(ms: number) {
  await act(async () => { await vi.advanceTimersByTimeAsync(ms) })
}

function setVisibilityState(state: DocumentVisibilityState) {
  Object.defineProperty(document, 'visibilityState', { configurable: true, value: state })
  fireEvent(document, new Event('visibilitychange'))
}

function renderLeaderboard() {
  return render(<MemoryRouter><LeaderboardPage /></MemoryRouter>)
}

beforeEach(() => {
  vi.restoreAllMocks()
  summaries.clear()
  vi.spyOn(apiClient, 'get').mockImplementation((url) => {
    const id = Number(String(url).split('/')[3])
    return Promise.resolve({ data: { id, username: summaries.get(id) } }) as never
  })
  vi.mocked(useAuth).mockReturnValue({
    isAuthenticated: false,
    claims: null,
  } as ReturnType<typeof useAuth>)
})

afterEach(() => {
  vi.useRealTimers()
  Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' })
})

describe('leaderboard loading, empty, and error states', () => {
  it('shows one accessible status while pending, then removes it after success', async () => {
    const request = deferred()
    vi.spyOn(leaderboardApi, 'getTop10').mockReturnValue(request.promise)
    renderLeaderboard()

    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status', { name: 'Loading leaderboard' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'No leaderboard entries yet' })).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    await act(async () => { request.resolve(response('Ada')) })
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'View profile for Ada' })).toBeInTheDocument()
  })

  it('hides old results in every commit when switching ranges and shows loading until the new response', async () => {
    const request = deferred()
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue(response('Old result'))
    vi.spyOn(leaderboardApi, 'getTop100').mockReturnValue(request.promise)
    const commits: boolean[] = []
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <Profiler id="leaderboard" onRender={() => {
          if (screen.queryByRole('button', { name: 'Top 100' })?.getAttribute('aria-pressed') === 'true') {
            commits.push(Boolean(screen.queryByRole('link', { name: 'View profile for Old result' })))
          }
        }}>
          <LeaderboardPage />
        </Profiler>
      </MemoryRouter>,
    )

    await screen.findByRole('link', { name: 'View profile for Old result' })
    await user.click(screen.getByRole('button', { name: 'Top 100' }))
    expect(commits.length).toBeGreaterThan(0)
    expect(commits).not.toContain(true)
    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status', { name: 'Loading leaderboard' })).toBeVisible()
    expect(screen.queryByRole('heading', { name: 'No leaderboard entries yet' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Top three podium' })).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Leaderboard rankings' })).not.toBeInTheDocument()

    await act(async () => { request.resolve(response('New result')) })
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'View profile for New result' })).toBeInTheDocument()
  })

  it('shows an honest empty state for both ranges, never while the next range is pending', async () => {
    const top100 = deferred()
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue(emptyResponse())
    vi.spyOn(leaderboardApi, 'getTop100').mockReturnValue(top100.promise)
    const user = userEvent.setup()
    renderLeaderboard()

    await screen.findByRole('heading', { name: 'No leaderboard entries yet' })
    expect(screen.getByText('There are no ranked users to display right now.')).toBeInTheDocument()
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Top three podium' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Top 100' }))
    expect(screen.getByRole('status', { name: 'Loading leaderboard' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'No leaderboard entries yet' })).not.toBeInTheDocument()
    await act(async () => { top100.resolve(emptyResponse()) })
    expect(screen.getByRole('heading', { name: 'No leaderboard entries yet' })).toBeInTheDocument()
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Top three podium' })).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Leaderboard rankings' })).not.toBeInTheDocument()
  })

  it('clears loading on failure, hides implementation details, and retries the selected range after repeated failures', async () => {
    const first = deferred()
    const retry = deferred()
    const top10 = vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue(response('Top 10'))
    const top100 = vi.spyOn(leaderboardApi, 'getTop100')
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(retry.promise)
      .mockResolvedValueOnce(response('Recovered'))
    const user = userEvent.setup()
    renderLeaderboard()
    await screen.findByRole('link', { name: 'View profile for Top 10' })
    await user.click(screen.getByRole('button', { name: 'Top 100' }))

    await act(async () => { first.reject(new Error('InternalException at /private/file:42\n    at service')) })
    const alert = screen.getByRole('alert')
    expect(within(alert).getByText('Unable to load leaderboard')).toBeInTheDocument()
    expect(within(alert).getByText('The leaderboard could not be loaded. Please try again.')).toBeInTheDocument()
    expect(alert).not.toHaveTextContent('InternalException')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'No leaderboard entries yet' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'View profile for Top 10' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(screen.getByRole('status', { name: 'Loading leaderboard' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    await act(async () => { retry.reject(new Error('Still unavailable')) })
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('link', { name: 'View profile for Recovered' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(top100).toHaveBeenCalledTimes(3)
    expect(top10).toHaveBeenCalledOnce()
  })

  it('retains the established network-error message', async () => {
    vi.spyOn(leaderboardApi, 'getTop10').mockRejectedValue(new AxiosError('Network Error'))
    renderLeaderboard()
    expect(await screen.findByRole('alert')).toHaveTextContent('Network error. Check your connection and try again.')
  })

  it('does not expose server-provided error details', async () => {
    const serverResponse = { ...response('unused'), status: 500, data: { message: 'SQL exception at internal service' } }
    vi.spyOn(leaderboardApi, 'getTop10').mockRejectedValue(new AxiosError('Request failed', undefined, undefined, undefined, serverResponse))
    renderLeaderboard()
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The leaderboard could not be loaded. Please try again.')
    expect(alert).not.toHaveTextContent('SQL exception')
  })
})

describe('leaderboard request sequencing', () => {
  it('ignores a stale error after a newer range succeeds', async () => {
    const top10 = deferred()
    vi.spyOn(leaderboardApi, 'getTop10').mockReturnValue(top10.promise)
    vi.spyOn(leaderboardApi, 'getTop100').mockResolvedValue(response('Latest'))
    const user = userEvent.setup()
    renderLeaderboard()
    await user.click(screen.getByRole('button', { name: 'Top 100' }))
    await screen.findByRole('link', { name: 'View profile for Latest' })

    await act(async () => { top10.reject(new Error('Stale failure')) })
    expect(screen.getByRole('link', { name: 'View profile for Latest' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('ignores a stale success after a newer range fails', async () => {
    const top10 = deferred()
    vi.spyOn(leaderboardApi, 'getTop10').mockReturnValue(top10.promise)
    vi.spyOn(leaderboardApi, 'getTop100').mockRejectedValue(new Error('Current failure'))
    const user = userEvent.setup()
    renderLeaderboard()
    await user.click(screen.getByRole('button', { name: 'Top 100' }))
    await screen.findByRole('alert')

    await act(async () => { top10.resolve(response('Stale')) })
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeEnabled()
    expect(screen.queryByRole('link', { name: 'View profile for Stale' })).not.toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('keeps the last selection through repeated rapid range switches', async () => {
    const first = deferred()
    const second = deferred()
    const third = deferred()
    const fourth = deferred()
    vi.spyOn(leaderboardApi, 'getTop10').mockReturnValueOnce(first.promise).mockReturnValueOnce(third.promise)
    vi.spyOn(leaderboardApi, 'getTop100').mockReturnValueOnce(second.promise).mockReturnValueOnce(fourth.promise)
    const user = userEvent.setup()
    renderLeaderboard()
    await user.click(screen.getByRole('button', { name: 'Top 100' }))
    await user.click(screen.getByRole('button', { name: 'Top 10' }))
    await user.click(screen.getByRole('button', { name: 'Top 100' }))

    await act(async () => { third.resolve(response('Third')) })
    await act(async () => { first.reject(new Error('First failure')) })
    await act(async () => { second.resolve(response('Second')) })
    expect(screen.getByRole('status', { name: 'Loading leaderboard' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Leaderboard rankings' })).not.toBeInTheDocument()
    await act(async () => { fourth.resolve(response('Fourth')) })
    expect(screen.getByRole('button', { name: 'Top 100' })).toHaveAttribute('aria-pressed', 'true')
    expect(await screen.findByRole('link', { name: 'View profile for Fourth' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'View profile for Third' })).not.toBeInTheDocument()
  })

  it('does not allow a pending retry to replace results after switching ranges', async () => {
    const retry = deferred()
    vi.spyOn(leaderboardApi, 'getTop10')
      .mockRejectedValueOnce(new Error('Initial failure'))
      .mockReturnValueOnce(retry.promise)
    vi.spyOn(leaderboardApi, 'getTop100').mockResolvedValue(response('Selected'))
    const user = userEvent.setup()
    renderLeaderboard()
    await screen.findByRole('alert')
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    await user.click(screen.getByRole('button', { name: 'Top 100' }))
    await screen.findByRole('link', { name: 'View profile for Selected' })

    await act(async () => { retry.resolve(response('Retry result')) })
    expect(screen.getByRole('link', { name: 'View profile for Selected' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'View profile for Retry result' })).not.toBeInTheDocument()
  })

  it.each(['resolve', 'reject'] as const)('handles a pending request that settles after unmount (%s)', async (outcome) => {
    const request = deferred()
    vi.spyOn(leaderboardApi, 'getTop10').mockReturnValue(request.promise)
    const consoleError = vi.spyOn(console, 'error')
    const { unmount } = renderLeaderboard()
    await flushPromises()
    unmount()

    await act(async () => {
      if (outcome === 'resolve') request.resolve(response('Unmounted'))
      else request.reject(new Error('Unmounted failure'))
    })
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'View profile for Unmounted' })).not.toBeInTheDocument()
    expect(consoleError).not.toHaveBeenCalled()
  })
})

describe('leaderboard polling', () => {
  it('coalesces the StrictMode effect replay into one initial request', async () => {
    const request = deferred()
    const getTop10 = vi.spyOn(leaderboardApi, 'getTop10').mockReturnValue(request.promise)
    render(
      <StrictMode>
        <MemoryRouter><LeaderboardPage /></MemoryRouter>
      </StrictMode>,
    )
    await flushPromises()

    expect(getTop10).toHaveBeenCalledOnce()
    await act(async () => { request.resolve(response('StrictMode result')) })
    expect(await screen.findByRole('link', { name: 'View profile for StrictMode result' })).toBeInTheDocument()
  })

  it.each([10, 100] as const)('refreshes the selected Top %i after each completed request without flicker', async (range) => {
    vi.useFakeTimers()
    const initialEntries = namedEntries(['Original Ada', 'Original Linus', 'Original Grace', 'Original Margaret'])
    const updatedEntries = namedEntries(['Newcomer', ...initialEntries.map((entry) => entry.username)])
    const refresh = deferred()
    const top10 = vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue(entriesResponse(initialEntries))
    const top100 = vi.spyOn(leaderboardApi, 'getTop100').mockResolvedValue(entriesResponse(initialEntries))
    const selectedApi = range === 10 ? top10 : top100
    renderLeaderboard()
    await flushPromises()

    if (range === 100) {
      fireEvent.click(screen.getByRole('button', { name: 'Top 100' }))
      await flushPromises()
    }

    selectedApi.mockReturnValueOnce(refresh.promise)
    await advanceTimersBy(5_000)
    expect(selectedApi).toHaveBeenCalledTimes(2)
    expect(screen.getByRole('link', { name: 'View profile for Original Ada' })).toBeInTheDocument()
    expect(screen.queryByRole('status', { name: 'Loading leaderboard' })).not.toBeInTheDocument()

    await advanceTimersBy(15_000)
    expect(selectedApi).toHaveBeenCalledTimes(2)

    await act(async () => { refresh.resolve(entriesResponse(updatedEntries)) })
    expect(screen.getAllByRole('link', { name: 'View profile for Original Ada' })).toHaveLength(1)
    const rankings = screen.getByRole('list', { name: 'Leaderboard rankings' })
    const fourthPlace = within(rankings).getByText('#4').closest('li') as HTMLElement
    expect(within(fourthPlace).getByRole('link', { name: 'View profile for Original Grace' })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /^View profile for / })).toHaveLength(updatedEntries.length)

    await advanceTimersBy(4_999)
    expect(selectedApi).toHaveBeenCalledTimes(2)
    await advanceTimersBy(1)
    expect(selectedApi).toHaveBeenCalledTimes(3)
  })

  it('keeps the last successful results through refresh failure and clears its warning after recovery', async () => {
    vi.useFakeTimers()
    const initialEntries = namedEntries(['Current Ada', 'Current Linus', 'Current Grace', 'Current Margaret'])
    const recoveredEntries = namedEntries(['Recovered Ada', 'Recovered Linus'])
    const recovery = deferred()
    const getTop10 = vi.spyOn(leaderboardApi, 'getTop10')
      .mockResolvedValueOnce(entriesResponse(initialEntries))
      .mockRejectedValueOnce(new Error('Internal refresh error'))
      .mockReturnValueOnce(recovery.promise)
      .mockResolvedValue(entriesResponse(recoveredEntries))
    renderLeaderboard()
    await flushPromises()

    await advanceTimersBy(5_000)
    expect(screen.getByText('Unable to refresh the leaderboard. Showing the last successfully loaded results.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View profile for Current Ada' })).toBeInTheDocument()
    expect(screen.queryByRole('status', { name: 'Loading leaderboard' })).not.toBeInTheDocument()

    await advanceTimersBy(5_000)
    expect(getTop10).toHaveBeenCalledTimes(3)
    expect(screen.getByRole('link', { name: 'View profile for Current Ada' })).toBeInTheDocument()
    await act(async () => { recovery.resolve(entriesResponse(recoveredEntries)) })
    expect(screen.getByRole('link', { name: 'View profile for Recovered Ada' })).toBeInTheDocument()
    expect(screen.queryByText('Unable to refresh the leaderboard. Showing the last successfully loaded results.')).not.toBeInTheDocument()
  })

  it('transitions between empty and populated results without a refresh spinner', async () => {
    vi.useFakeTimers()
    const populatedEntries = namedEntries(['Ada', 'Linus', 'Grace', 'Margaret'])
    const populate = deferred()
    const emptyAgain = deferred()
    const getTop10 = vi.spyOn(leaderboardApi, 'getTop10')
      .mockResolvedValueOnce(emptyResponse())
      .mockReturnValueOnce(populate.promise)
      .mockReturnValueOnce(emptyAgain.promise)
    renderLeaderboard()
    await flushPromises()
    expect(screen.getByRole('heading', { name: 'No leaderboard entries yet' })).toBeInTheDocument()

    await advanceTimersBy(5_000)
    expect(screen.getByRole('heading', { name: 'No leaderboard entries yet' })).toBeInTheDocument()
    expect(screen.queryByRole('status', { name: 'Loading leaderboard' })).not.toBeInTheDocument()
    await act(async () => { populate.resolve(entriesResponse(populatedEntries)) })
    expect(screen.getByRole('region', { name: 'Top three podium' })).toBeInTheDocument()

    await advanceTimersBy(5_000)
    expect(screen.getByRole('region', { name: 'Top three podium' })).toBeInTheDocument()
    expect(getTop10).toHaveBeenCalledTimes(3)
    await act(async () => { emptyAgain.resolve(emptyResponse()) })
    expect(screen.getByRole('heading', { name: 'No leaderboard entries yet' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Top three podium' })).not.toBeInTheDocument()
  })

  it('pauses while hidden, refreshes immediately on visibility, and never overlaps requests', async () => {
    vi.useFakeTimers()
    const initialEntries = namedEntries(['Ada'])
    const refresh = deferred()
    const getTop10 = vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue(entriesResponse(initialEntries))
    renderLeaderboard()
    await flushPromises()
    getTop10.mockReturnValueOnce(refresh.promise)

    setVisibilityState('hidden')
    await advanceTimersBy(15_000)
    expect(getTop10).toHaveBeenCalledOnce()

    setVisibilityState('visible')
    expect(getTop10).toHaveBeenCalledTimes(2)
    setVisibilityState('visible')
    await advanceTimersBy(15_000)
    expect(getTop10).toHaveBeenCalledTimes(2)

    await act(async () => { refresh.resolve(entriesResponse(namedEntries(['Updated Ada']))) })
    await advanceTimersBy(4_999)
    expect(getTop10).toHaveBeenCalledTimes(2)
    await advanceTimersBy(1)
    expect(getTop10).toHaveBeenCalledTimes(3)
  })

  it('coordinates manual retry with the poll timer and pending request', async () => {
    vi.useFakeTimers()
    const recoveredEntries = namedEntries(['Recovered Ada'])
    const retry = deferred()
    const getTop10 = vi.spyOn(leaderboardApi, 'getTop10')
      .mockRejectedValueOnce(new Error('Unavailable'))
      .mockReturnValueOnce(retry.promise)
      .mockResolvedValue(entriesResponse(recoveredEntries))
    renderLeaderboard()
    await flushPromises()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeEnabled()

    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(screen.getByRole('status', { name: 'Loading leaderboard' })).toBeInTheDocument()
    await advanceTimersBy(5_000)
    expect(getTop10).toHaveBeenCalledTimes(2)

    await act(async () => { retry.resolve(entriesResponse(recoveredEntries)) })
    expect(screen.getByRole('link', { name: 'View profile for Recovered Ada' })).toBeInTheDocument()
    await advanceTimersBy(5_000)
    expect(getTop10).toHaveBeenCalledTimes(3)
  })

  it('cleans up the previous range timer and stops polling after unmount', async () => {
    vi.useFakeTimers()
    const initialEntries = namedEntries(['Ada'])
    const getTop10 = vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue(entriesResponse(initialEntries))
    const getTop100 = vi.spyOn(leaderboardApi, 'getTop100').mockResolvedValue(entriesResponse(initialEntries))
    const view = renderLeaderboard()
    await flushPromises()

    fireEvent.click(screen.getByRole('button', { name: 'Top 100' }))
    await flushPromises()
    await advanceTimersBy(5_000)
    expect(getTop10).toHaveBeenCalledOnce()
    expect(getTop100).toHaveBeenCalledTimes(2)

    view.unmount()
    await advanceTimersBy(10_000)
    expect(getTop100).toHaveBeenCalledTimes(2)
  })
})
