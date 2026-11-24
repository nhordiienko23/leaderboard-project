import axios from 'axios'
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../../api/auth'
import * as commentsApi from '../../api/comments'
import * as likesApi from '../../api/likes'
import * as postsApi from '../../api/posts'
import { setAccessToken } from '../../api/client'
import { AuthProvider, useAuth } from '../../auth/AuthContext'
import { clearRefreshToken, REFRESH_TOKEN_STORAGE_KEY } from '../../auth/authState'
import { LikeActions } from '../../features/likes/LikeActions'
import { PostDetailPage } from './PostDetailPage'

const post = {
  id: 7,
  content: 'Engagement post',
  userId: 42,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

function tokenFor(userId: number) {
  return `header.${btoa(JSON.stringify({ sub: String(userId), role: 'USER' }))}.signature`
}

function restore(userId = 42) {
  sessionStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, 'refresh')
  vi.spyOn(authApi, 'refresh').mockResolvedValue({
    data: { accessToken: tokenFor(userId), refreshToken: 'rotated', tokenType: 'Bearer', expiresIn: 900 },
  } as never)
}

function renderActions(postId = 7, commentId?: number) {
  return render(
    <MemoryRouter>
      <AuthProvider><LikeActions postId={postId} commentId={commentId} /></AuthProvider>
    </MemoryRouter>,
  )
}

function responseError(status: number, message: string) {
  return new axios.AxiosError('failed', 'ERR_BAD_REQUEST', undefined, undefined, {
    data: { status, message },
    status,
    statusText: 'Error',
    headers: {},
    config: {} as never,
  })
}

beforeEach(() => {
  vi.restoreAllMocks()
  sessionStorage.clear()
  clearRefreshToken()
  setAccessToken(null)
})

describe('Phase 4 engagement summaries', () => {
  it('shows anonymous post and comment totals without mutation controls', async () => {
    vi.spyOn(postsApi, 'getPostEngagement').mockResolvedValue({
      data: { likeCount: 4, commentCount: 6, likedByMe: null },
    } as never)
    vi.spyOn(commentsApi, 'getCommentEngagement').mockResolvedValue({
      data: { likeCount: 3, likedByMe: null },
    } as never)

    const postView = renderActions()
    expect(await screen.findByLabelText('4 likes for post 7')).toHaveTextContent('4')
    expect(screen.getByRole('link', { name: '6 comments on post 7' })).toHaveAttribute('href', '/posts/7#comments')
    expect(screen.queryByRole('button', { name: 'Like post 7' })).not.toBeInTheDocument()
    postView.unmount()

    renderActions(7, 3)
    expect(await screen.findByLabelText('3 likes for comment 3')).toHaveTextContent('3')
    expect(screen.queryByRole('button', { name: 'Like comment 3' })).not.toBeInTheDocument()
  })

  it('uses confirmed authenticated liked and unliked states accessibly', async () => {
    restore()
    const engagement = vi.spyOn(postsApi, 'getPostEngagement').mockResolvedValue({
      data: { likeCount: 5, commentCount: 2, likedByMe: true },
    } as never)
    const likedView = renderActions()
    const liked = await screen.findByRole('button', { name: 'Like post 7' })
    const unlike = screen.getByRole('button', { name: 'Unlike post 7' })
    await waitFor(() => {
      expect(liked).toHaveAttribute('aria-pressed', 'true')
      expect(unlike).toBeEnabled()
    })
    expect(liked).toBeDisabled()
    expect(liked).toHaveTextContent('5')
    likedView.unmount()

    vi.mocked(authApi.refresh).mockResolvedValue({
      data: { accessToken: tokenFor(42), refreshToken: 'rotated', tokenType: 'Bearer', expiresIn: 900 },
    } as never)
    engagement.mockResolvedValue({ data: { likeCount: 5, commentCount: 2, likedByMe: false } } as never)
    renderActions()
    const unlikeState = await screen.findByRole('button', { name: 'Unlike post 7' })
    const likeState = screen.getByRole('button', { name: 'Like post 7' })
    await waitFor(() => {
      expect(likeState).toBeEnabled()
      expect(unlikeState).toBeDisabled()
    })
    expect(likeState).toHaveAttribute('aria-pressed', 'false')
  })

  it('refreshes post state after Like and unlike mutations', async () => {
    restore()
    const summaries = vi.spyOn(postsApi, 'getPostEngagement')
      .mockResolvedValueOnce({ data: { likeCount: 1, commentCount: 2, likedByMe: false } } as never)
      .mockResolvedValueOnce({ data: { likeCount: 2, commentCount: 2, likedByMe: true } } as never)
      .mockResolvedValueOnce({ data: { likeCount: 1, commentCount: 2, likedByMe: false } } as never)
    const like = vi.spyOn(likesApi, 'likePost').mockResolvedValue({} as never)
    const unlike = vi.spyOn(likesApi, 'unlikePost').mockResolvedValue({} as never)
    const user = userEvent.setup()
    renderActions()

    await user.click(await screen.findByRole('button', { name: 'Like post 7' }))
    expect(like).toHaveBeenCalledWith(7)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Unlike post 7' })).toBeEnabled())
    expect(screen.getByRole('button', { name: 'Like post 7' })).toHaveTextContent('2')

    await user.click(screen.getByRole('button', { name: 'Unlike post 7' }))
    expect(unlike).toHaveBeenCalledWith(7)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Like post 7' })).toBeEnabled())
    expect(summaries).toHaveBeenCalledTimes(3)
  })

  it('refreshes after duplicate-like and missing-like responses', async () => {
    restore()
    vi.spyOn(postsApi, 'getPostEngagement')
      .mockResolvedValueOnce({ data: { likeCount: 1, commentCount: 2, likedByMe: false } } as never)
      .mockResolvedValueOnce({ data: { likeCount: 2, commentCount: 2, likedByMe: true } } as never)
      .mockResolvedValueOnce({ data: { likeCount: 1, commentCount: 2, likedByMe: false } } as never)
    vi.spyOn(likesApi, 'likePost').mockRejectedValue(responseError(409, 'You have already liked this post'))
    vi.spyOn(likesApi, 'unlikePost').mockRejectedValue(responseError(404, 'Like not found'))
    const user = userEvent.setup()
    renderActions()

    await user.click(await screen.findByRole('button', { name: 'Like post 7' }))
    expect(await screen.findByText('This item is already liked.')).toBeInTheDocument()
    const unlike = await screen.findByRole('button', { name: 'Unlike post 7' })
    await waitFor(() => expect(unlike).toBeEnabled())
    await user.click(unlike)
    expect(await screen.findByText('No existing like was found.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Like post 7' })).toBeEnabled())
  })

  it('disables actions while loading and retries a failed summary without hiding the resource', async () => {
    restore()
    let rejectSummary!: (reason: unknown) => void
    const engagement = vi.spyOn(postsApi, 'getPostEngagement')
      .mockReturnValueOnce(new Promise((_resolve, reject) => { rejectSummary = reject }) as never)
      .mockResolvedValueOnce({ data: { likeCount: 8, commentCount: 4, likedByMe: false } } as never)
    render(
      <MemoryRouter>
        <AuthProvider>
          <p>Post remains visible</p>
          <LikeActions postId={7} />
        </AuthProvider>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByRole('button', { name: 'Like post 7' })).toBeDisabled())
    await waitFor(() => expect(engagement).toHaveBeenCalledTimes(1))
    expect(screen.getByRole('button', { name: 'Unlike post 7' })).toBeDisabled()
    expect(screen.queryByText('0')).not.toBeInTheDocument()
    await act(async () => rejectSummary(responseError(503, 'Summary unavailable')))
    expect(await screen.findByRole('alert')).toHaveTextContent('Summary unavailable')
    expect(screen.getByText('Post remains visible')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Retry engagement for post 7' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Like post 7' })).toHaveTextContent('8'))
  })

  it('discards stale summaries when the resource ID changes', async () => {
    let resolveOld!: (value: never) => void
    vi.spyOn(postsApi, 'getPostEngagement')
      .mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve }) as never)
      .mockResolvedValueOnce({ data: { likeCount: 9, commentCount: 1, likedByMe: null } } as never)

    const view = renderActions(7)
    await screen.findByRole('status', { name: 'Loading engagement for post 7' })
    view.rerender(
      <MemoryRouter>
        <AuthProvider><LikeActions postId={8} /></AuthProvider>
      </MemoryRouter>,
    )
    expect(await screen.findByLabelText('9 likes for post 8')).toHaveTextContent('9')
    await act(async () => resolveOld({ data: { likeCount: 1, commentCount: 1, likedByMe: null } } as never))
    expect(screen.queryByLabelText('1 likes for post 8')).not.toBeInTheDocument()
    expect(screen.getByLabelText('9 likes for post 8')).toBeInTheDocument()
  })

  it('clears viewer-specific state on account switch and logout', async () => {
    let resolveFirstViewer!: (value: never) => void
    vi.spyOn(postsApi, 'getPostEngagement')
      .mockResolvedValueOnce({ data: { likeCount: 1, commentCount: 1, likedByMe: null } } as never)
      .mockReturnValueOnce(new Promise((resolve) => { resolveFirstViewer = resolve }) as never)
      .mockResolvedValueOnce({ data: { likeCount: 9, commentCount: 1, likedByMe: false } } as never)
      .mockResolvedValueOnce({ data: { likeCount: 4, commentCount: 1, likedByMe: null } } as never)
    vi.spyOn(authApi, 'login').mockImplementation(async ({ login }) => ({
      data: {
        accessToken: tokenFor(login === 'one' ? 1 : 2),
        refreshToken: `${login}-refresh`,
        tokenType: 'Bearer',
        expiresIn: 900,
      },
    }) as never)
    vi.spyOn(authApi, 'logout').mockResolvedValue({} as never)

    function Harness() {
      const auth = useAuth()
      return (
        <>
          <button onClick={() => void auth.login({ login: 'one', password: 'password' })}>Use one</button>
          <button onClick={() => void auth.login({ login: 'two', password: 'password' })}>Use two</button>
          <button onClick={() => void auth.logout()}>Log out</button>
          <LikeActions postId={7} />
        </>
      )
    }

    const user = userEvent.setup()
    render(<MemoryRouter><AuthProvider><Harness /></AuthProvider></MemoryRouter>)
    expect(await screen.findByLabelText('1 likes for post 7')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Use one' }))
    await waitFor(() => expect(screen.queryByLabelText('1 likes for post 7')).not.toBeInTheDocument())
    await user.click(screen.getByRole('button', { name: 'Use two' }))
    expect(await screen.findByRole('button', { name: 'Like post 7' })).toHaveTextContent('9')
    await act(async () => resolveFirstViewer({ data: { likeCount: 2, commentCount: 1, likedByMe: true } } as never))
    expect(screen.getByRole('button', { name: 'Like post 7' })).toHaveTextContent('9')

    await user.click(screen.getByRole('button', { name: 'Log out' }))
    expect(await screen.findByLabelText('4 likes for post 7')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Like post 7' })).not.toBeInTheDocument()
  })

  it('links post comment totals to a stable detail anchor', async () => {
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(postsApi, 'getPostEngagement').mockResolvedValue({
      data: { likeCount: 5, commentCount: 7, likedByMe: null },
    } as never)
    vi.spyOn(commentsApi, 'getComments').mockResolvedValue({
      data: { content: [], totalElements: 0, totalPages: 0, size: 5, number: 0, first: true, last: true },
    } as never)
    render(
      <MemoryRouter initialEntries={['/posts/7']}>
        <AuthProvider>
          <Routes><Route path="/posts/:postId" element={<PostDetailPage />} /></Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    const link = await screen.findByRole('link', { name: '7 comments on post 7' })
    expect(link).toHaveAttribute('href', '/posts/7#comments')
    expect(document.getElementById('comments')).toHaveAttribute('aria-labelledby', 'comments-heading')
  })

  it('refreshes the parent post comment count after comment creation and deletion', async () => {
    restore()
    const createdComment = {
      id: 3,
      content: 'Created comment',
      userId: 42,
      postId: 7,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    }
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    const engagement = vi.spyOn(postsApi, 'getPostEngagement')
      .mockResolvedValueOnce({ data: { likeCount: 5, commentCount: 0, likedByMe: false } } as never)
      .mockResolvedValueOnce({ data: { likeCount: 5, commentCount: 1, likedByMe: false } } as never)
      .mockResolvedValueOnce({ data: { likeCount: 5, commentCount: 0, likedByMe: false } } as never)
    vi.spyOn(commentsApi, 'getComments')
      .mockResolvedValueOnce({
        data: { content: [], totalElements: 0, totalPages: 0, size: 5, number: 0, first: true, last: true },
      } as never)
      .mockResolvedValueOnce({
        data: { content: [createdComment], totalElements: 1, totalPages: 1, size: 5, number: 0, first: true, last: true },
      } as never)
      .mockResolvedValueOnce({
        data: { content: [], totalElements: 0, totalPages: 0, size: 5, number: 0, first: true, last: true },
      } as never)
    vi.spyOn(commentsApi, 'createComment').mockResolvedValue({ data: createdComment } as never)
    vi.spyOn(commentsApi, 'deleteComment').mockResolvedValue({} as never)
    vi.spyOn(commentsApi, 'getCommentEngagement').mockResolvedValue({
      data: { likeCount: 0, likedByMe: false },
    } as never)
    const user = userEvent.setup()

    render(
      <MemoryRouter initialEntries={['/posts/7']}>
        <AuthProvider>
          <Routes><Route path="/posts/:postId" element={<PostDetailPage />} /></Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('link', { name: '0 comments on post 7' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Create a comment'), 'Created comment')
    await user.click(screen.getByRole('button', { name: 'Comment' }))
    expect(await screen.findByRole('link', { name: '1 comments on post 7' })).toBeInTheDocument()
    const composer = screen.getByLabelText('Create a comment')
    await user.type(composer, 'Unfinished draft')

    await user.click(screen.getByRole('button', { name: 'Delete comment 3' }))
    const dialog = screen.getByRole('dialog', { name: 'Delete comment' })
    await user.click(within(dialog).getByRole('button', { name: 'Delete comment' }))
    expect(await screen.findByRole('link', { name: '0 comments on post 7' })).toBeInTheDocument()
    expect(screen.getByLabelText('Create a comment')).toBe(composer)
    expect(composer).toHaveValue('Unfinished draft')
    expect(engagement).toHaveBeenCalledTimes(3)
  })
})
