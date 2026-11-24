import axios from 'axios'
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as likesApi from '../../api/likes'
import * as postsApi from '../../api/posts'
import { LikeActions } from './LikeActions'

vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ status: 'authenticated', isAuthenticated: true, claims: { sub: '42' } }),
}))

const unliked = { data: { likeCount: 1, commentCount: 2, likedByMe: false } }
const liked = { data: { likeCount: 2, commentCount: 2, likedByMe: true } }

function responseError(status: number, message: string) {
  return new axios.AxiosError('failed', 'ERR_BAD_REQUEST', undefined, undefined, {
    data: { status, message },
    status,
    statusText: 'Error',
    headers: {},
    config: {} as never,
  })
}

async function renderActions() {
  let view!: ReturnType<typeof render>
  await act(async () => {
    view = render(<MemoryRouter><LikeActions postId={7} /></MemoryRouter>)
  })
  return view
}

async function clickAction(name: string) {
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name }))
  })
}

function advance(milliseconds: number) {
  act(() => vi.advanceTimersByTime(milliseconds))
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.spyOn(postsApi, 'getPostEngagement')
    .mockResolvedValueOnce(unliked as never)
    .mockResolvedValue(liked as never)
  vi.spyOn(likesApi, 'likePost').mockResolvedValue({} as never)
  vi.spyOn(likesApi, 'unlikePost').mockResolvedValue({} as never)
})

afterEach(() => {
  cleanup()
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('reaction feedback dismissal', () => {
  it('keeps successful feedback visible until the final 400ms, then dismisses it at 4 seconds', async () => {
    await renderActions()
    await clickAction('Like post 7')
    const notice = screen.getByText('Like confirmed.').parentElement!
    const wrapper = notice.parentElement!

    advance(3599)
    expect(notice).toBeInTheDocument()
    expect(wrapper).not.toHaveClass('motion-safe:opacity-0')
    advance(1)
    expect(wrapper).toHaveClass('motion-safe:opacity-0', 'motion-safe:transition-opacity', 'motion-safe:duration-[400ms]')
    advance(399)
    expect(notice).toBeInTheDocument()
    advance(1)
    expect(notice).not.toBeInTheDocument()
  })

  it.each([
    ['duplicate like', 'likePost', 'Like post 7', 409, 'Already liked', 'This item is already liked.'],
    ['missing like', 'unlikePost', 'Unlike post 7', 404, 'Like not found', 'No existing like was found.'],
  ] as const)('dismisses the %s warning after 4 seconds', async (_case, method, button, status, message, feedback) => {
    if (method === 'unlikePost') vi.mocked(postsApi.getPostEngagement).mockReset().mockResolvedValue(liked as never)
    vi.mocked(likesApi[method]).mockRejectedValue(responseError(status, message))
    await renderActions()
    await clickAction(button)

    advance(3999)
    expect(screen.getByText(feedback)).toBeInTheDocument()
    advance(1)
    expect(screen.queryByText(feedback)).not.toBeInTheDocument()
  })

  it('uses only motion-safe fade styles while retaining the same dismissal deadline', async () => {
    await renderActions()
    await clickAction('Like post 7')
    const wrapper = screen.getByText('Like confirmed.').parentElement!.parentElement!

    advance(3600)
    expect(wrapper).toHaveClass('motion-safe:opacity-0', 'motion-safe:transition-opacity')
    expect(wrapper).not.toHaveClass('opacity-0', 'transition-opacity')
    advance(399)
    expect(screen.getByText('Like confirmed.')).toBeInTheDocument()
    advance(1)
    expect(screen.queryByText('Like confirmed.')).not.toBeInTheDocument()
  })

  it('does not allow an older notice to fade or dismiss its replacement', async () => {
    await renderActions()
    await clickAction('Like post 7')
    advance(3800)
    expect(screen.getByText('Like confirmed.').parentElement!.parentElement!).toHaveClass('motion-safe:opacity-0')
    await clickAction('Unlike post 7')
    const replacement = screen.getByText('Like removed.')
    const wrapper = replacement.parentElement!.parentElement!

    expect(wrapper).not.toHaveClass('motion-safe:opacity-0', 'motion-safe:transition-opacity')
    advance(200)
    expect(replacement).toBeInTheDocument()
    expect(wrapper).not.toHaveClass('motion-safe:opacity-0')
    advance(3399)
    expect(wrapper).not.toHaveClass('motion-safe:opacity-0')
    advance(1)
    expect(wrapper).toHaveClass('motion-safe:opacity-0')
    advance(400)
    expect(replacement).not.toBeInTheDocument()
  })

  it('keeps a replacement error visible after the previous deadline', async () => {
    vi.mocked(likesApi.unlikePost).mockRejectedValue(responseError(503, 'Service unavailable'))
    await renderActions()
    await clickAction('Like post 7')
    advance(3800)
    await clickAction('Unlike post 7')
    advance(10000)
    expect(screen.getByRole('alert')).toHaveTextContent('Service unavailable')
  })

  it('cleans up both timers on unmount', async () => {
    const view = await renderActions()
    await clickAction('Like post 7')
    expect(vi.getTimerCount()).toBe(2)
    view.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })

  it.each([
    ['network', new axios.AxiosError('Network Error'), 'Network error. Check your connection and try again.'],
    ['authentication', responseError(401, 'Please sign in'), 'Please sign in'],
    ['authorization', responseError(403, 'Access denied'), 'Access denied'],
    ['missing resource', responseError(404, 'Post not found'), 'Post not found'],
    ['server', responseError(503, 'Service unavailable'), 'Service unavailable'],
  ])('does not automatically dismiss a %s error', async (_case, error, message) => {
    vi.mocked(likesApi.likePost).mockRejectedValue(error)
    await renderActions()
    await clickAction('Like post 7')

    expect(screen.getByRole('alert')).toHaveTextContent(message)
    advance(10000)
    expect(screen.getByRole('alert')).toHaveTextContent(message)
    expect(vi.getTimerCount()).toBe(0)
  })
})
