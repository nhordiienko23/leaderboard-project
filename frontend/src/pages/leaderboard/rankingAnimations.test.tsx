import { AxiosHeaders, type AxiosResponse } from 'axios'
import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { Link, MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as leaderboardApi from '../../api/leaderboard'
import type { LeaderboardEntryDTO } from '../../api/types'
import { useAuth } from '../../auth/AuthContext'
import { LeaderboardPage } from './LeaderboardPage'

vi.mock('../../auth/AuthContext', () => ({ useAuth: vi.fn() }))
vi.mock('../../features/users/UserName', () => ({
  UserName: ({ userId }: { userId: number }) => <Link to={`/users/${userId}`}>User {userId}</Link>,
}))

const entries: LeaderboardEntryDTO[] = Array.from({ length: 6 }, (_, index) => ({
  userId: index + 1, username: `User ${index + 1}`, points: 100 - index,
}))
const reordered = [entries[0], entries[1], entries[2], entries[4], entries[3], entries[5]]

function response(data: LeaderboardEntryDTO[]): AxiosResponse<LeaderboardEntryDTO[]> {
  return { data, status: 200, statusText: 'OK', headers: {}, config: { headers: new AxiosHeaders() } }
}

interface TestAnimation {
  cancel: ReturnType<typeof vi.fn>
  onfinish: (() => void) | null
  oncancel: (() => void) | null
}

const animations: TestAnimation[] = []
const animate = vi.fn((_frames: Keyframe[], _options: KeyframeAnimationOptions) => {
  const animation: TestAnimation = { cancel: vi.fn(), onfinish: null, oncancel: null }
  animations.push(animation)
  return animation
})
const originalAnimate = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'animate')
let reduced = false
let motionListener: (() => void) | undefined
let removeMotionListener: ReturnType<typeof vi.fn>

function row(userId: number) {
  const element = screen.getByRole('link', { name: `User ${userId}` }).closest('li')
  if (!element) throw new Error('Missing ranking row')
  return element
}

function framesFor(element: Element) {
  return animate.mock.calls.filter((_, index) => animate.mock.contexts[index] === element).map(([frames]) => frames)
}

async function flush() {
  await act(async () => { await Promise.resolve() })
}

async function poll() {
  await act(async () => { await vi.advanceTimersByTimeAsync(5_000) })
}

async function mount(size: 10 | 100 = 10) {
  const view = render(<MemoryRouter><LeaderboardPage /></MemoryRouter>)
  await flush()
  if (size === 100) {
    fireEvent.click(screen.getByRole('button', { name: 'Top 100' }))
    await flush()
  }
  return view
}

beforeEach(() => {
  vi.restoreAllMocks()
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  animate.mockClear()
  animations.length = 0
  reduced = false
  motionListener = undefined
  removeMotionListener = vi.fn()
  vi.stubGlobal('matchMedia', vi.fn(() => ({
    get matches() { return reduced },
    addEventListener: vi.fn((_event, listener: () => void) => { motionListener = listener }),
    removeEventListener: removeMotionListener,
  })))
  Object.defineProperty(HTMLElement.prototype, 'animate', { configurable: true, value: animate })
  vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
    const siblings = Array.from(this.parentElement?.children ?? [])
    const index = siblings.indexOf(this)
    const podium = this.parentElement?.parentElement?.getAttribute('aria-label') === 'Top three podium'
    return new DOMRect(podium ? [100, 0, 200][index] : 0, podium ? 0 : 200 + index * 40, 100, 40)
  })
  vi.mocked(useAuth).mockReturnValue({
    status: 'authenticated', isAuthenticated: true, isAdmin: false, claims: { sub: 4, role: 'USER' },
    login: vi.fn(), register: vi.fn(), logout: vi.fn(), clearAuth: vi.fn(),
  })
  vi.spyOn(leaderboardApi, 'getTop10').mockResolvedValue(response(entries))
  vi.spyOn(leaderboardApi, 'getTop100').mockResolvedValue(response(entries))
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  if (originalAnimate) Object.defineProperty(HTMLElement.prototype, 'animate', originalAnimate)
  else Reflect.deleteProperty(HTMLElement.prototype, 'animate')
})

describe('leaderboard ranking animations', () => {
  it.each([10, 100] as const)('moves users up and down in Top %i while retaining keyed rows and links', async (size) => {
    const initialEntries = size === 100
      ? Array.from({ length: 100 }, (_, index) => ({
          userId: index + 1, username: `User ${index + 1}`, points: 200 - index,
        }))
      : entries
    const getSelected = size === 10 ? leaderboardApi.getTop10 : leaderboardApi.getTop100
    vi.mocked(getSelected).mockResolvedValue(response(initialEntries))
    await mount(size)
    const oldFourth = row(4)
    const oldFifth = row(5)
    vi.mocked(getSelected).mockResolvedValue(response([
      ...initialEntries.slice(0, 3), initialEntries[4], initialEntries[3], ...initialEntries.slice(5),
    ]))

    await poll()

    expect(row(4)).toBe(oldFourth)
    expect(row(5)).toBe(oldFifth)
    expect(within(row(4)).getByText('#5')).toBeInTheDocument()
    expect(within(row(5)).getByText('#4')).toBeInTheDocument()
    expect(framesFor(row(4))).toEqual([[{ transform: 'translate(0px, -40px)' }, { transform: 'translate(0px, 0px)' }]])
    expect(framesFor(row(5))).toEqual([[{ transform: 'translate(0px, 40px)' }, { transform: 'translate(0px, 0px)' }]])
    expect(within(row(4)).getByRole('link')).toHaveAttribute('href', '/users/4')
    expect(within(row(4)).getByText('You')).toBeInTheDocument()
    expect(row(4)).toHaveClass('border-l-2', 'bg-violet-500/10')
    expect(animate).toHaveBeenCalledTimes(2)
    expect(screen.getAllByRole('listitem')).toHaveLength(initialEntries.length)
    expect(animate.mock.calls[0][1]).toEqual({ duration: 300, easing: 'cubic-bezier(0.2, 0, 0, 1)' })
  })

  it('moves keyed podium users between medal positions', async () => {
    await mount()
    const first = row(1)
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response([entries[1], entries[0], ...entries.slice(2)]))
    await poll()

    expect(row(1)).toBe(first)
    expect(row(1)).toHaveClass('col-start-1')
    expect(within(row(1)).getByText('2nd place')).toBeInTheDocument()
    expect(framesFor(row(1))[0][0]).toEqual({ transform: 'translate(100px, 0px)' })
    expect(framesFor(row(2))[0][0]).toEqual({ transform: 'translate(-100px, 0px)' })
    expect(within(row(2)).getByRole('img', { name: 'First place medal' })).toBeInTheDocument()
  })

  it('tracks movement across the podium/list boundary by user ID rather than fading as a new user', async () => {
    await mount()
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response([
      entries[0], entries[1], entries[3], entries[2], ...entries.slice(4),
    ]))
    await poll()

    expect(framesFor(row(4))[0][0]).toEqual({ transform: 'translate(-200px, 200px)' })
    expect(framesFor(row(3))[0][0]).toEqual({ transform: 'translate(200px, -200px)' })
    expect(within(row(4)).getByText('3rd place')).toBeInTheDocument()
    expect(within(row(4)).getByText('You')).toBeInTheDocument()
    expect(row(4)).toHaveClass('ring-2')
    expect(screen.getAllByRole('link', { name: 'User 4' })).toHaveLength(1)
    expect(within(row(3)).getByRole('link')).toHaveAttribute('href', '/users/3')
  })

  it('fades new users in and removes departed users and links immediately', async () => {
    await mount()
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response([
      ...entries.slice(0, 4), { userId: 7, username: 'User 7', points: 90 },
    ]))
    await poll()

    expect(framesFor(row(7))).toEqual([[{ opacity: 0 }, { opacity: 1 }]])
    expect(screen.queryByRole('link', { name: 'User 5' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'User 6' })).not.toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(5)
    expect(animate).toHaveBeenCalledOnce()
  })

  it('softens changed points on both the podium and list without a counter or row movement', async () => {
    await mount()
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response(
      entries.map((entry) => entry.userId === 1 || entry.userId === 4 ? { ...entry, points: 200 } : entry),
    ))
    await poll()

    for (const userId of [1, 4]) {
      const points = within(row(userId)).getByText('200')
      expect(framesFor(points)).toEqual([[{ opacity: 0.55 }, { opacity: 1 }]])
      expect(framesFor(row(userId))).toEqual([])
    }
    expect(animate).toHaveBeenCalledTimes(2)
  })

  it('does not measure or replay animations for unchanged polling responses', async () => {
    await mount()
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response(reordered))
    await poll()
    animate.mockClear()
    vi.mocked(HTMLElement.prototype.getBoundingClientRect).mockClear()
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response(
      reordered.map((entry) => ({ ...entry, username: 'Changed projection name' })),
    ))
    await poll()
    await poll()

    expect(animate).not.toHaveBeenCalled()
    expect(HTMLElement.prototype.getBoundingClientRect).not.toHaveBeenCalled()
  })

  it('does not animate or measure initial loading or the first successful response', async () => {
    let resolve!: (value: AxiosResponse<LeaderboardEntryDTO[]>) => void
    vi.mocked(leaderboardApi.getTop10).mockReturnValue(new Promise((done) => { resolve = done }))
    await mount()
    expect(screen.getByRole('status', { name: 'Loading leaderboard' })).toBeInTheDocument()
    await act(async () => { resolve(response(entries)) })

    expect(row(1)).toBeInTheDocument()
    expect(animate).not.toHaveBeenCalled()
    expect(HTMLElement.prototype.getBoundingClientRect).not.toHaveBeenCalled()
  })

  it('does not treat switching ranges as rank changes, but animates the next background change', async () => {
    await mount()
    vi.mocked(leaderboardApi.getTop100).mockResolvedValue(response(reordered))
    fireEvent.click(screen.getByRole('button', { name: 'Top 100' }))
    await flush()
    expect(within(row(4)).getByText('#5')).toBeInTheDocument()
    expect(animate).not.toHaveBeenCalled()
    vi.mocked(leaderboardApi.getTop100).mockResolvedValue(response(entries))
    await poll()
    expect(animate).toHaveBeenCalledTimes(2)

    fireEvent.click(screen.getByRole('button', { name: 'Top 10' }))
    await flush()
    expect(animations.every((animation) => animation.cancel.mock.calls.length === 1)).toBe(true)
    expect(animate).toHaveBeenCalledTimes(2)
  })

  it('uses immediate updates and no measurements for reduced motion', async () => {
    reduced = true
    await mount()
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response([
      entries[1], entries[0], entries[2], { ...entries[4], points: 200 }, entries[3],
      { userId: 7, username: 'User 7', points: 90 },
    ]))
    await poll()

    expect(within(row(5)).getByText('200')).toBeInTheDocument()
    expect(within(row(1)).getByText('2nd place')).toBeInTheDocument()
    expect(row(7)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'User 6' })).not.toBeInTheDocument()
    expect(animate).not.toHaveBeenCalled()
    expect(HTMLElement.prototype.getBoundingClientRect).not.toHaveBeenCalled()
  })

  it('cancels active animations when reduced motion is enabled and removes the listener on unmount', async () => {
    const view = await mount()
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response(reordered))
    await poll()
    reduced = true
    act(() => { motionListener?.() })
    expect(animations.every((animation) => animation.cancel.mock.calls.length === 1)).toBe(true)
    animate.mockClear()
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response(entries))
    await poll()
    expect(animate).not.toHaveBeenCalled()
    view.unmount()
    expect(removeMotionListener).toHaveBeenCalledWith('change', motionListener)
  })

  it('cancels unfinished animations on unmount and releases finished animations', async () => {
    const view = await mount()
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response(reordered))
    await poll()
    animations[0].onfinish?.()
    view.unmount()
    expect(animations[0].cancel).not.toHaveBeenCalled()
    expect(animations[1].cancel).toHaveBeenCalledOnce()
  })

  it('does not animate refresh errors or the recovery response, then resumes on a consecutive success', async () => {
    await mount()
    vi.mocked(leaderboardApi.getTop10).mockRejectedValueOnce(new Error('Unavailable'))
    await poll()
    expect(screen.getByRole('status')).toHaveTextContent('Leaderboard refresh delayed')
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response(reordered))
    await poll()
    expect(within(row(4)).getByText('#5')).toBeInTheDocument()
    expect(animate).not.toHaveBeenCalled()
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response(entries))
    await poll()
    expect(animate).toHaveBeenCalledTimes(2)
  })

  it('does not animate foreground retries', async () => {
    vi.mocked(leaderboardApi.getTop10).mockRejectedValueOnce(new Error('Unavailable'))
    await mount()
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    await flush()
    expect(row(1)).toBeInTheDocument()
    expect(animate).not.toHaveBeenCalled()
  })

  it('does not animate empty states or the first results after an empty response', async () => {
    await mount()
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response([]))
    await poll()
    expect(screen.getByRole('heading', { name: 'No leaderboard entries yet' })).toBeInTheDocument()
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response(reordered))
    await poll()
    expect(row(4)).toBeInTheDocument()
    expect(animate).not.toHaveBeenCalled()
  })

  it('updates immediately when Web Animations is unavailable', async () => {
    Reflect.deleteProperty(HTMLElement.prototype, 'animate')
    await mount()
    vi.mocked(leaderboardApi.getTop10).mockResolvedValue(response(reordered))
    await poll()
    expect(within(row(4)).getByText('#5')).toBeInTheDocument()
    expect(animate).not.toHaveBeenCalled()
    expect(HTMLElement.prototype.getBoundingClientRect).not.toHaveBeenCalled()
  })
})
