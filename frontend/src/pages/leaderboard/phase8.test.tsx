import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as leaderboardApi from '../../api/leaderboard'
import type { LeaderboardEntryDTO } from '../../api/types'
import { apiClient } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import { LeaderboardPage } from './LeaderboardPage'

vi.mock('../../auth/AuthContext', () => ({ useAuth: vi.fn() }))

const usernames = ['Ada', 'Linus', 'Grace', 'Margaret', 'Barbara', 'Donald', 'Katherine', 'Edsger', 'Frances', 'Guido']
let nextUserId = 1000
const summaries = new Map<number, string>()

function createEntries(count = 10): LeaderboardEntryDTO[] {
  return Array.from({ length: count }, (_, index) => {
    const userId = nextUserId++
    const username = usernames[index] ?? `Member ${index + 1}`
    summaries.set(userId, username)
    return { userId, username, points: 240 - index * 10 }
  })
}

function renderLeaderboard() {
  return render(<MemoryRouter><LeaderboardPage /></MemoryRouter>)
}

function setAuthenticatedSubject(sub: unknown) {
  vi.mocked(useAuth).mockReturnValue({
    isAuthenticated: true,
    claims: { sub, role: 'USER' },
  } as ReturnType<typeof useAuth>)
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

describe('Phase 8 leaderboard', () => {
  it('renders the top three in podium positions and ranks the remaining entries from four', async () => {
    const entries = createEntries()
    entries[3].points = 5
    entries[4].points = 999
    const top10 = vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: entries } as never)
    const top100 = vi.spyOn(leaderboardApi, 'getTop100')
    renderLeaderboard()

    expect(screen.getByRole('heading', { name: 'Leaderboard' })).toBeInTheDocument()
    expect(screen.getByText('See how the community ranks by points.')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Leaderboard trophy' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Top 10' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Top 100' })).toHaveAttribute('aria-pressed', 'false')

    await screen.findByRole('link', { name: 'View profile for Guido' })
    const podium = screen.getByRole('region', { name: 'Top three podium' })
    const firstPlace = within(podium).getByText('1st place').closest('li') as HTMLElement
    const secondPlace = within(podium).getByText('2nd place').closest('li') as HTMLElement
    const thirdPlace = within(podium).getByText('3rd place').closest('li') as HTMLElement
    const podiumList = within(podium).getByRole('list')
    expect(within(podium).getAllByRole('listitem')).toEqual([firstPlace, secondPlace, thirdPlace])
    expect(podiumList).toHaveClass('grid-cols-3', 'items-end', 'gap-2', 'sm:gap-4')
    expect(firstPlace).toHaveClass('col-start-2')
    expect(secondPlace).toHaveClass('col-start-1')
    expect(thirdPlace).toHaveClass('col-start-3')
    expect(within(firstPlace).getByRole('img', { name: 'First place medal' })).toBeInTheDocument()
    expect(within(secondPlace).getByRole('img', { name: 'Second place medal' })).toBeInTheDocument()
    expect(within(thirdPlace).getByRole('img', { name: 'Third place medal' })).toBeInTheDocument()
    expect(within(firstPlace).getByRole('link', { name: 'View profile for Ada' })).toHaveAttribute('href', `/users/${entries[0].userId}`)
    expect(within(secondPlace).getByRole('link', { name: 'View profile for Linus' })).toHaveAttribute('href', `/users/${entries[1].userId}`)
    expect(within(thirdPlace).getByRole('link', { name: 'View profile for Grace' })).toHaveAttribute('href', `/users/${entries[2].userId}`)
    expect(within(firstPlace).getByText('240')).toBeInTheDocument()
    expect(within(secondPlace).getByText(String(entries[1].points))).toBeInTheDocument()
    expect(within(thirdPlace).getByText(String(entries[2].points))).toBeInTheDocument()

    const rankings = screen.getByRole('list', { name: 'Leaderboard rankings' })
    const rows = within(rankings).getAllByRole('listitem')
    expect(rows).toHaveLength(7)
    for (let rank = 4; rank <= 10; rank += 1) {
      const row = rows[rank - 4]
      expect(within(row).getByText(`#${rank}`)).toBeInTheDocument()
      expect(within(row).getByRole('link', { name: `View profile for ${entries[rank - 1].username}` })).toBeInTheDocument()
      expect(within(row).getByText(String(entries[rank - 1].points))).toHaveClass('text-right')
    }
    for (const entry of entries.slice(0, 3)) {
      expect(within(rankings).queryByRole('link', { name: `View profile for ${entry.username}` })).not.toBeInTheDocument()
    }
    expect(top10).toHaveBeenCalledOnce()
    expect(top100).not.toHaveBeenCalled()
  })

  it('keeps the range selector content-sized and the podium responsive', async () => {
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: createEntries(3) } as never)
    renderLeaderboard()

    const selector = screen.getByRole('group', { name: 'Leaderboard size' })
    expect(selector).toHaveClass('inline-flex')
    expect(within(selector).getByRole('button', { name: 'Top 10' })).toHaveClass('min-h-9')
    expect(within(selector).getByRole('button', { name: 'Top 100' })).toHaveClass('min-h-9')

    const podium = await screen.findByRole('region', { name: 'Top three podium' })
    expect(within(podium).getByRole('list')).toHaveClass('grid-cols-3', 'gap-2', 'sm:gap-4')
  })

  it('uses authoritative summary names instead of leaderboard projection names', async () => {
    const entries = createEntries(4)
    entries[0].username = 'Leaderboard Ada'
    entries[3].username = 'Leaderboard member'
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: entries } as never)
    renderLeaderboard()

    const podium = await screen.findByRole('region', { name: 'Top three podium' })
    expect(await within(podium).findByRole('link', { name: 'View profile for Ada' }))
      .toHaveAttribute('href', `/users/${entries[0].userId}`)
    const rankings = screen.getByRole('list', { name: 'Leaderboard rankings' })
    expect(await within(rankings).findByRole('link', { name: 'View profile for Margaret' }))
      .toHaveAttribute('href', `/users/${entries[3].userId}`)
    expect(screen.queryByText('Leaderboard Ada')).not.toBeInTheDocument()
    expect(screen.queryByText('Leaderboard member')).not.toBeInTheDocument()
    expect(apiClient.get).toHaveBeenCalledTimes(4)
  })

  it('shows loading, deleted and retryable states without linking or falling back to projection names', async () => {
    const entries = createEntries(2)
    entries[0].username = 'Deleted projection'
    entries[1].username = 'Failed projection'
    let rejectDeleted!: (reason: unknown) => void
    let rejectUnavailable!: (reason: unknown) => void
    vi.mocked(apiClient.get)
      .mockReturnValueOnce(new Promise((_, reject) => { rejectDeleted = reject }) as never)
      .mockReturnValueOnce(new Promise((_, reject) => { rejectUnavailable = reject }) as never)
      .mockResolvedValueOnce({ data: { id: entries[1].userId, username: 'Recovered authoritative' } } as never)
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: entries } as never)
    renderLeaderboard()

    const podium = await screen.findByRole('region', { name: 'Top three podium' })
    expect(within(podium).getAllByText('Loading username...')).toHaveLength(2)
    expect(within(podium).queryByRole('link')).not.toBeInTheDocument()
    await act(async () => {
      rejectDeleted({ isAxiosError: true, response: { status: 404, data: { message: 'Not found' } } })
      rejectUnavailable(new Error('Service unavailable'))
    })
    expect(within(podium).getByText('Deleted user')).toBeInTheDocument()
    expect(within(podium).getByText(/Username unavailable/)).toBeInTheDocument()
    expect(within(podium).queryByRole('link')).not.toBeInTheDocument()
    expect(screen.queryByText('Deleted projection')).not.toBeInTheDocument()
    expect(screen.queryByText('Failed projection')).not.toBeInTheDocument()
    await userEvent.click(within(podium).getByRole('button', { name: 'Retry username' }))
    expect(await within(podium).findByRole('link', { name: 'View profile for Recovered authoritative' }))
      .toHaveAttribute('href', `/users/${entries[1].userId}`)
    expect(apiClient.get).toHaveBeenCalledTimes(3)
  })

  it('shows an empty state and no podium for zero Top 10 entries', async () => {
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: [] } as never)
    renderLeaderboard()

    expect(await screen.findByRole('heading', { name: 'No leaderboard entries yet' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Top three podium' })).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Leaderboard rankings' })).not.toBeInTheDocument()
  })

  it('renders a single Top 10 entry at first place without an empty secondary list', async () => {
    const [entry] = createEntries(1)
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: [entry] } as never)
    renderLeaderboard()

    const podium = await screen.findByRole('region', { name: 'Top three podium' })
    expect(within(podium).getByText('1st place')).toBeInTheDocument()
    expect(await within(podium).findByRole('link', { name: `View profile for ${entry.username}` })).toBeInTheDocument()
    expect(within(podium).queryByText('2nd place')).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Leaderboard rankings' })).not.toBeInTheDocument()
  })

  it('renders two Top 10 entries at second and first place without inventing a third', async () => {
    const entries = createEntries(2)
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: entries } as never)
    renderLeaderboard()

    const podium = await screen.findByRole('region', { name: 'Top three podium' })
    expect(within(podium).getByText('2nd place')).toBeInTheDocument()
    expect(within(podium).getByText('1st place')).toBeInTheDocument()
    expect(within(podium).queryByText('3rd place')).not.toBeInTheDocument()
    expect(within(podium).getAllByRole('listitem')).toHaveLength(2)
  })

  it('shows the Top 100 podium and remaining results in backend order', async () => {
    const entries = createEntries(6)
    const top100 = vi.spyOn(leaderboardApi, 'getTop100').mockResolvedValue({ data: entries } as never)
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: createEntries(1) } as never)
    const user = userEvent.setup()
    renderLeaderboard()

    await screen.findByRole('link', { name: 'View profile for Ada' })
    await user.click(screen.getByRole('button', { name: 'Top 100' }))

    const rankings = await screen.findByRole('list', { name: 'Leaderboard rankings' })
    const podium = screen.getByRole('region', { name: 'Top three podium' })
    expect(within(podium).getByRole('link', { name: 'View profile for Ada' })).toBeInTheDocument()
    expect(within(podium).getByRole('link', { name: 'View profile for Linus' })).toBeInTheDocument()
    expect(within(podium).getByRole('link', { name: 'View profile for Grace' })).toBeInTheDocument()
    await within(rankings).findByRole('link', { name: `View profile for ${entries[entries.length - 1].username}` })
    const links = within(rankings).getAllByRole('link')
    expect(links.map((link) => link.getAttribute('aria-label'))).toEqual(
      entries.slice(3).map((entry) => `View profile for ${entry.username}`),
    )
    expect(within(rankings).getByText('#4')).toBeInTheDocument()
    expect(within(rankings).getByText('#6')).toBeInTheDocument()
    for (const entry of entries.slice(0, 3)) {
      expect(within(rankings).queryByRole('link', { name: `View profile for ${entry.username}` })).not.toBeInTheDocument()
    }
    expect(top100).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: 'Top 100' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'Top 10' })).toHaveAttribute('aria-pressed', 'false')
  })

  it('renders 100 Top 100 entries once in returned order with their ranks and points', async () => {
    const entries = createEntries(100)
    entries[0].points = 1
    entries[99].points = 9999
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: [] } as never)
    vi.spyOn(leaderboardApi, 'getTop100').mockResolvedValue({ data: entries } as never)
    setAuthenticatedSubject(entries[50].userId)
    const user = userEvent.setup()
    renderLeaderboard()

    await screen.findByRole('heading', { name: 'No leaderboard entries yet' })
    await user.click(screen.getByRole('button', { name: 'Top 100' }))

    const rankings = await screen.findByRole('list', { name: 'Leaderboard rankings' })
    await within(rankings).findByRole('link', { name: `View profile for ${entries[99].username}` })
    const podium = screen.getByRole('region', { name: 'Top three podium' })
    expect(within(podium).getAllByRole('listitem')).toHaveLength(3)
    const rows = within(rankings).getAllByRole('listitem')
    expect(rows).toHaveLength(entries.length - 3)
    entries.forEach((entry, index) => {
      const location = index < 3 ? podium : rows[index - 3]
      if (index >= 3) expect(within(location).getByText(`#${index + 1}`)).toBeInTheDocument()
      expect(within(location).getByRole('link', { name: `View profile for ${entry.username}` })).toBeInTheDocument()
      expect(within(location).getByText(String(entry.points))).toBeInTheDocument()
    })
    expect(within(rows[47]).getByText('#51')).toBeInTheDocument()
    expect(within(rows[47]).getByText('You')).toBeInTheDocument()
    expect(rows[47]).toHaveClass('border-l-2', 'bg-violet-500/10')
    expect(screen.getAllByText('You')).toHaveLength(1)
    expect(screen.getAllByRole('link', { name: /^View profile for / })).toHaveLength(100)
  })

  it.each([1, 2, 3])('renders a short Top 100 result of %i entries only on the podium', async (count) => {
    const entries = createEntries(count)
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: [] } as never)
    vi.spyOn(leaderboardApi, 'getTop100').mockResolvedValue({ data: entries } as never)
    const user = userEvent.setup()
    renderLeaderboard()

    await screen.findByRole('heading', { name: 'No leaderboard entries yet' })
    await user.click(screen.getByRole('button', { name: 'Top 100' }))

    const podium = await screen.findByRole('region', { name: 'Top three podium' })
    expect(within(podium).getAllByRole('listitem')).toHaveLength(count)
    expect(screen.queryByRole('list', { name: 'Leaderboard rankings' })).not.toBeInTheDocument()
    entries.forEach((entry, index) => {
      const place = index === 0 ? '1st place' : index === 1 ? '2nd place' : '3rd place'
      expect(within(podium).getByText(place)).toBeInTheDocument()
      expect(within(podium).getByRole('link', { name: `View profile for ${entry.username}` })).toBeInTheDocument()
      expect(screen.getAllByRole('link', { name: `View profile for ${entry.username}` })).toHaveLength(1)
    })
  })

  it('highlights the authenticated user in the podium', async () => {
    const entries = createEntries()
    setAuthenticatedSubject(entries[1].userId)
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: entries } as never)
    renderLeaderboard()

    const podium = await screen.findByRole('region', { name: 'Top three podium' })
    const currentUser = within(podium).getByText('2nd place').closest('li') as HTMLElement
    expect(within(currentUser).getByText('You')).toBeInTheDocument()
    expect(currentUser).toHaveClass('bg-violet-500/10', 'ring-2')
    expect(screen.getAllByText('You')).toHaveLength(1)
    for (const otherUser of within(podium).getAllByRole('listitem').filter((item) => item !== currentUser)) {
      expect(otherUser).not.toHaveClass('ring-2')
      expect(within(otherUser).queryByText('You')).not.toBeInTheDocument()
    }
  })

  it('keeps long podium usernames and ranking-row points inside their entries', async () => {
    const entries = createEntries(4)
    const longUsername = 'exceptionallylongcommunityusernamewithoutanyspaces1234567890'
    entries[0].username = longUsername
    summaries.set(entries[0].userId, longUsername)
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: entries } as never)
    setAuthenticatedSubject(entries[3].userId)
    renderLeaderboard()

    const podium = await screen.findByRole('region', { name: 'Top three podium' })
    const firstPlace = (await within(podium).findByRole('link', { name: `View profile for ${longUsername}` })).closest('li') as HTMLElement
    expect(firstPlace).toContainElement(within(firstPlace).getByRole('img', { name: 'First place medal' }))
    expect(within(firstPlace).getByText(String(entries[0].points))).toBeInTheDocument()

    const rankings = screen.getByRole('list', { name: 'Leaderboard rankings' })
    const currentUser = within(rankings).getByText('#4').closest('li') as HTMLElement
    expect(within(currentUser).getByText('You')).toBeInTheDocument()
    expect(within(currentUser).getByText(String(entries[3].points))).toBeInTheDocument()
  })

  it('highlights the authenticated user in the ranking list', async () => {
    const entries = createEntries(5)
    setAuthenticatedSubject(String(entries[4].userId))
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: entries } as never)
    renderLeaderboard()

    const rankings = await screen.findByRole('list', { name: 'Leaderboard rankings' })
    const currentUser = within(rankings).getByText('#5').closest('li') as HTMLElement
    expect(within(currentUser).getByText('You')).toBeInTheDocument()
    expect(currentUser).toHaveClass('border-l-2', 'bg-violet-500/10')
    expect(await within(currentUser).findByRole('link', { name: `View profile for ${entries[4].username}` })).toBeInTheDocument()
    expect(screen.getAllByText('You')).toHaveLength(1)
    expect(within(rankings).getByText('#4').closest('li')).not.toHaveClass('border-l-2')
    expect(within(rankings).getByText('#4').closest('li')).not.toHaveClass('bg-violet-500/10')
  })

  it('keeps one correctly positioned current-user entry when switching ranges', async () => {
    const entries = createEntries(5)
    const currentUser = entries[1]
    setAuthenticatedSubject(String(currentUser.userId).padStart(6, '0'))
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: entries } as never)
    vi.spyOn(leaderboardApi, 'getTop100').mockResolvedValue({
      data: [entries[0], entries[2], entries[3], currentUser, entries[4]],
    } as never)
    const user = userEvent.setup()
    renderLeaderboard()

    const profileName = `View profile for ${currentUser.username}`
    let profile = await screen.findByRole('link', { name: profileName })
    let row = profile.closest('li') as HTMLElement
    expect(within(row).getByText('2nd place')).toBeInTheDocument()
    expect(within(row).getByText('You')).toBeInTheDocument()
    expect(row).toHaveClass('ring-2')
    expect(screen.getAllByRole('link', { name: profileName })).toHaveLength(1)
    expect(screen.getAllByText('You')).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: 'Top 100' }))
    const rankings = await screen.findByRole('list', { name: 'Leaderboard rankings' })
    profile = await within(rankings).findByRole('link', { name: profileName })
    row = profile.closest('li') as HTMLElement
    expect(within(row).getByText('#4')).toBeInTheDocument()
    expect(within(row).getByText('You')).toBeInTheDocument()
    expect(row).toHaveClass('border-l-2', 'bg-violet-500/10')
    expect(screen.getAllByRole('link', { name: profileName })).toHaveLength(1)
    expect(screen.getAllByText('You')).toHaveLength(1)
    for (const otherUser of within(rankings).getAllByRole('listitem').filter((item) => item !== row)) {
      expect(otherUser).not.toHaveClass('border-l-2')
      expect(within(otherUser).queryByText('You')).not.toBeInTheDocument()
    }

    await user.click(screen.getByRole('button', { name: 'Top 10' }))
    const podium = await screen.findByRole('region', { name: 'Top three podium' })
    profile = await within(podium).findByRole('link', { name: profileName })
    row = profile.closest('li') as HTMLElement
    expect(within(row).getByText('2nd place')).toBeInTheDocument()
    expect(within(row).getByText('You')).toBeInTheDocument()
    expect(row).toHaveClass('ring-2')
    expect(screen.getAllByRole('link', { name: profileName })).toHaveLength(1)
    expect(screen.getAllByText('You')).toHaveLength(1)
    expect(apiClient.get).toHaveBeenCalledTimes(entries.length)
  })

  it.each([
    undefined, null, '', ' 1000', '1e3', '-1', '0', 0, -1, 1.5, Number.NaN,
    Number.MAX_SAFE_INTEGER + 1, '9007199254740992', {},
  ])('does not highlight entries for invalid authenticated subject %s', async (subject) => {
    const entries = createEntries(4)
    setAuthenticatedSubject(subject)
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: entries } as never)
    renderLeaderboard()

    await screen.findByRole('link', { name: `View profile for ${entries[3].username}` })
    expect(screen.queryByText('You')).not.toBeInTheDocument()
    for (const item of screen.getAllByRole('listitem')) {
      expect(item).not.toHaveClass('ring-2')
      expect(item).not.toHaveClass('border-l-2')
      expect(item).not.toHaveClass('bg-violet-500/10')
    }
  })

  it('does not highlight entries when authenticated claims are missing', async () => {
    const entries = createEntries(4)
    vi.mocked(useAuth).mockReturnValue({
      isAuthenticated: true,
      claims: null,
    } as ReturnType<typeof useAuth>)
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: entries } as never)
    renderLeaderboard()

    await screen.findByRole('link', { name: `View profile for ${entries[3].username}` })
    expect(screen.queryByText('You')).not.toBeInTheDocument()
  })

  it('does not invent a ranking for a current user outside the results', async () => {
    const entries = createEntries(4)
    setAuthenticatedSubject(nextUserId + 1)
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: entries } as never)
    vi.spyOn(leaderboardApi, 'getTop100').mockResolvedValue({ data: entries } as never)
    const user = userEvent.setup()
    renderLeaderboard()

    const rankings = await screen.findByRole('list', { name: 'Leaderboard rankings' })
    expect(within(rankings).getAllByRole('listitem')).toHaveLength(1)
    expect(screen.queryByText('You')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Top 100' }))
    const top100Rankings = await screen.findByRole('list', { name: 'Leaderboard rankings' })
    expect(within(screen.getByRole('region', { name: 'Top three podium' })).getAllByRole('listitem')).toHaveLength(3)
    expect(within(top100Rankings).getAllByRole('listitem')).toHaveLength(entries.length - 3)
    expect(screen.queryByText('You')).not.toBeInTheDocument()
  })

  it('allows a long ranking username to shrink and wrap beside right-aligned points', async () => {
    const entries = createEntries(4)
    const longUsername = 'exceptionallylongcommunityusernamewithoutanyspaces1234567890'
    entries[3].username = longUsername
    summaries.set(entries[3].userId, longUsername)
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: entries } as never)
    renderLeaderboard()

    const link = await screen.findByRole('link', { name: `View profile for ${longUsername}` })
    const usernameWrapper = link.parentElement as HTMLElement
    const usernameCell = usernameWrapper.parentElement as HTMLElement
    const row = link.closest('li') as HTMLElement
    expect(usernameWrapper).toHaveClass('min-w-0', 'max-w-full')
    expect(usernameCell).toHaveClass('min-w-0', 'flex-1', 'flex-wrap', 'break-words')
    expect(within(row).getByText(String(entries[3].points))).toHaveClass('shrink-0', 'text-right')
  })

  it('does not highlight any entry for an anonymous user', async () => {
    const entries = createEntries()
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: entries } as never)
    renderLeaderboard()

    await screen.findByRole('region', { name: 'Top three podium' })
    expect(screen.queryByText('You')).not.toBeInTheDocument()
  })

  it('shows a loading state while the leaderboard request is pending', () => {
    vi.spyOn(leaderboardApi, 'getTop10').mockReturnValue(new Promise(() => {}) as never)
    renderLeaderboard()

    expect(screen.getByRole('status', { name: 'Loading leaderboard' })).toBeInTheDocument()
  })

  it('shows request errors and retries the selected leaderboard', async () => {
    const entries = createEntries()
    const getTop10 = vi.spyOn(leaderboardApi, 'getTop10')
      .mockRejectedValueOnce(new Error('Leaderboard unavailable'))
      .mockResolvedValueOnce({ data: entries } as never)
    const user = userEvent.setup()
    renderLeaderboard()

    expect(await screen.findByText('The leaderboard could not be loaded. Please try again.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('link', { name: 'View profile for Ada' })).toBeInTheDocument()
    await waitFor(() => expect(getTop10).toHaveBeenCalledTimes(2))
  })

  it('keeps the newest range results when an older request resolves later', async () => {
    const oldEntries = createEntries(1)
    const latestEntries = createEntries(2)
    oldEntries[0].username = 'Old response'
    latestEntries[0].username = 'Latest response'
    summaries.set(oldEntries[0].userId, 'Old response')
    summaries.set(latestEntries[0].userId, 'Latest response')
    let resolveTop10!: (response: { data: LeaderboardEntryDTO[] }) => void
    const pendingTop10 = new Promise<{ data: LeaderboardEntryDTO[] }>((resolve) => { resolveTop10 = resolve })
    vi.spyOn(leaderboardApi, 'getTop10').mockReturnValue(pendingTop10 as never)
    vi.spyOn(leaderboardApi, 'getTop100').mockResolvedValue({ data: latestEntries } as never)
    const user = userEvent.setup()
    renderLeaderboard()

    await user.click(screen.getByRole('button', { name: 'Top 100' }))
    const podium = await screen.findByRole('region', { name: 'Top three podium' })
    expect(await within(podium).findByRole('link', { name: 'View profile for Latest response' })).toBeInTheDocument()

    await act(async () => { resolveTop10({ data: oldEntries }) })
    expect(screen.getByRole('button', { name: 'Top 100' })).toHaveAttribute('aria-pressed', 'true')
    expect(within(podium).getByRole('link', { name: 'View profile for Latest response' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'View profile for Old response' })).not.toBeInTheDocument()
  })

  it('keeps the existing Top 10 and Top 100 API calls when switching ranges', async () => {
    vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue({ data: createEntries(1) } as never)
    const getTop100 = vi.spyOn(leaderboardApi, 'getTop100').mockResolvedValue({ data: createEntries(1) } as never)
    const user = userEvent.setup()
    renderLeaderboard()

    await screen.findByRole('region', { name: 'Top three podium' })
    await user.click(screen.getByRole('button', { name: 'Top 100' }))
    await screen.findByRole('region', { name: 'Top three podium' })
    await user.click(screen.getByRole('button', { name: 'Top 10' }))

    expect(await screen.findByRole('region', { name: 'Top three podium' })).toBeInTheDocument()
    expect(getTop100).toHaveBeenCalledOnce()
    expect(leaderboardApi.getTop10).toHaveBeenCalledTimes(2)
  })
})
