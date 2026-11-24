import axios from 'axios'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../../api/auth'
import * as postsApi from '../../api/posts'
import * as commentsApi from '../../api/comments'
import * as likesApi from '../../api/likes'
import { setAccessToken } from '../../api/client'
import { AuthProvider } from '../../auth/AuthContext'
import { clearRefreshToken, REFRESH_TOKEN_STORAGE_KEY } from '../../auth/authState'
import { PostsPage } from './PostsPage'
import { PostDetailPage } from './PostDetailPage'

const post = { id: 7, content: 'Post seven', userId: 42, createdAt: '2026-01-01', updatedAt: '2026-01-01' }
const comment = { id: 3, content: 'Comment three', userId: 42, postId: 7, createdAt: '2026-01-01', updatedAt: '2026-01-01' }
const page = <T,>(content: T[], number = 0, totalPages = 1) => ({
  content, number, size: 5, totalElements: content.length, totalPages, first: number === 0, last: number >= totalPages - 1,
})

function failure(status: number, message: string) {
  return new axios.AxiosError('failed', 'ERR_BAD_REQUEST', undefined, undefined, {
    data: { status, message }, status, statusText: 'Error', headers: {}, config: {} as never,
  })
}

function renderFeed() {
  return render(<MemoryRouter><AuthProvider><PostsPage /></AuthProvider></MemoryRouter>)
}

function renderDetail() {
  return render(
    <MemoryRouter initialEntries={['/posts/7']}>
      <AuthProvider><Routes><Route path="/posts/:postId" element={<PostDetailPage />} /></Routes></AuthProvider>
    </MemoryRouter>,
  )
}

function authenticate() {
  sessionStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, 'refresh')
  const token = `header.${btoa(JSON.stringify({ sub: '42', role: 'USER' }))}.signature`
  vi.spyOn(authApi, 'refresh').mockResolvedValue({
    data: { accessToken: token, refreshToken: 'rotated', tokenType: 'Bearer', expiresIn: 900 },
  } as never)
}

beforeEach(() => {
  vi.restoreAllMocks()
  clearRefreshToken()
  setAccessToken(null)
  vi.spyOn(postsApi, 'getPostEngagement').mockResolvedValue({ data: { likeCount: 2, commentCount: 1, likedByMe: false } } as never)
  vi.spyOn(commentsApi, 'getCommentEngagement').mockResolvedValue({ data: { likeCount: 1, likedByMe: false } } as never)
})

describe('post pagination and errors', () => {
  it('refetches and moves back once when the last post on page two is deleted', async () => {
    authenticate()
    let resolvePrevious!: (value: never) => void
    const get = vi.spyOn(postsApi, 'getPosts')
      .mockResolvedValueOnce({ data: page([{ ...post, id: 8, content: 'Earlier page' }], 0, 2) } as never)
      .mockResolvedValueOnce({ data: page([post], 1, 2) } as never)
      .mockResolvedValueOnce({ data: page([], 1, 1) } as never)
      .mockReturnValueOnce(new Promise((resolve) => { resolvePrevious = resolve }) as never)
    const remove = vi.spyOn(postsApi, 'deletePost').mockResolvedValue({ status: 204 } as never)
    const user = userEvent.setup()
    renderFeed()
    await screen.findByText('Earlier page')
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    await screen.findByText('Post seven')
    await user.click(await screen.findByRole('button', { name: 'Delete post 7' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete post' }))
    await waitFor(() => expect(get).toHaveBeenCalledTimes(4))
    expect(remove).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('status', { name: 'Loading posts' })).toBeInTheDocument()
    expect(get).toHaveBeenNthCalledWith(3, { page: 1, size: 5, sort: 'id,desc' })
    expect(get).toHaveBeenNthCalledWith(4, { page: 0, size: 5, sort: 'id,desc' })
    await act(async () => resolvePrevious({ data: page([{ ...post, id: 8, content: 'Earlier page' }]) } as never))
    expect(await screen.findByText('Earlier page')).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(4)
  })

  it('shows the empty state when the last post on the first page is deleted', async () => {
    authenticate()
    vi.spyOn(postsApi, 'getPosts').mockResolvedValueOnce({ data: page([post]) } as never)
      .mockResolvedValueOnce({ data: page([], 0, 0) } as never)
    vi.spyOn(postsApi, 'deletePost').mockResolvedValue({} as never)
    const user = userEvent.setup()
    renderFeed()
    await user.click(await screen.findByRole('button', { name: 'Delete post 7' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete post' }))
    expect(await screen.findByRole('heading', { name: 'No posts yet' })).toBeInTheDocument()
  })

  it('does not refetch posts when deletion finishes after the page is left', async () => {
    authenticate()
    const get = vi.spyOn(postsApi, 'getPosts').mockResolvedValue({ data: page([post]) } as never)
    let resolveDelete!: (value: never) => void
    vi.spyOn(postsApi, 'deletePost').mockReturnValue(new Promise((resolve) => { resolveDelete = resolve }) as never)
    const user = userEvent.setup()
    const rendered = renderFeed()
    await user.click(await screen.findByRole('button', { name: 'Delete post 7' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete post' }))
    rendered.unmount()
    await act(async () => resolveDelete({ status: 204 } as never))
    expect(get).toHaveBeenCalledTimes(1)
  })

  it.each([403, 404])('shows a contextual %s feed error with retry', async (status) => {
    vi.spyOn(postsApi, 'getPosts').mockRejectedValue(failure(status, 'Feed unavailable'))
    renderFeed()
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(status === 403 ? 'Forbidden' : 'Posts not found')
    expect(alert).toHaveTextContent('Feed unavailable')
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it.each([403, 404])('shows a contextual %s detail error', async (status) => {
    vi.spyOn(postsApi, 'getPost').mockRejectedValue(failure(status, 'Unavailable'))
    renderDetail()
    expect(await screen.findByText(status === 403 ? 'Forbidden' : 'Post not found')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Back to posts/ })).toBeInTheDocument()
  })

  it.each([403, 404])('keeps post mutation %s errors in the dialog', async (status) => {
    authenticate()
    vi.spyOn(postsApi, 'getPosts').mockResolvedValue({ data: page([post]) } as never)
    vi.spyOn(postsApi, 'deletePost').mockRejectedValue(failure(status, 'Cannot delete this post'))
    const user = userEvent.setup()
    renderFeed()
    await user.click(await screen.findByRole('button', { name: 'Delete post 7' }))
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Delete post' }))
    const alert = await within(dialog).findByRole('alert')
    expect(alert).toHaveTextContent(status === 403 ? 'Forbidden' : 'Not found')
    expect(alert).toHaveTextContent('Cannot delete this post')
    expect(screen.getByText('Post seven')).toBeInTheDocument()
  })

  it.each([403, 404])('shows post creation %s errors without losing the form', async (status) => {
    authenticate()
    vi.spyOn(postsApi, 'getPosts').mockResolvedValue({ data: page([post]) } as never)
    vi.spyOn(postsApi, 'createPost').mockRejectedValue(failure(status, 'Cannot create post'))
    const user = userEvent.setup()
    renderFeed()
    const input = await screen.findByLabelText('Create a post')
    fireEvent.change(input, { target: { value: 'Draft post' } })
    await user.click(screen.getByRole('button', { name: 'Post' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(status === 403 ? 'Forbidden' : 'Not found')
    expect(alert).toHaveTextContent('Cannot create post')
    expect(input).toHaveValue('Draft post')
  })

  it.each([403, 404])('keeps post edit %s errors in the dialog', async (status) => {
    authenticate()
    vi.spyOn(postsApi, 'getPosts').mockResolvedValue({ data: page([post]) } as never)
    vi.spyOn(postsApi, 'updatePost').mockRejectedValue(failure(status, 'Cannot edit post'))
    const user = userEvent.setup()
    renderFeed()
    await user.click(await screen.findByRole('button', { name: 'Edit post 7' }))
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))
    const alert = await within(dialog).findByRole('alert')
    expect(alert).toHaveTextContent(status === 403 ? 'Forbidden' : 'Not found')
    expect(alert).toHaveTextContent('Cannot edit post')
  })
})

describe('comment pagination, ordering, and errors', () => {
  it('refetches and moves back once when the last comment on page two is deleted', async () => {
    authenticate()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    let resolvePrevious!: (value: never) => void
    const get = vi.spyOn(commentsApi, 'getComments')
      .mockResolvedValueOnce({ data: page([{ ...comment, id: 2, content: 'Earlier comment' }], 0, 2) } as never)
      .mockResolvedValueOnce({ data: page([comment], 1, 2) } as never)
      .mockResolvedValueOnce({ data: page([], 1, 1) } as never)
      .mockReturnValueOnce(new Promise((resolve) => { resolvePrevious = resolve }) as never)
    const remove = vi.spyOn(commentsApi, 'deleteComment').mockResolvedValue({ status: 204 } as never)
    const user = userEvent.setup()
    renderDetail()
    await screen.findByText('Earlier comment')
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    await screen.findByText('Comment three')
    await user.click(await screen.findByRole('button', { name: 'Delete comment 3' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete comment' }))
    await waitFor(() => expect(get).toHaveBeenCalledTimes(4))
    expect(remove).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('status', { name: 'Loading comments' })).toBeInTheDocument()
    expect(get).toHaveBeenNthCalledWith(3, 7, { page: 1, size: 5, sort: 'id,asc' })
    expect(get).toHaveBeenNthCalledWith(4, 7, { page: 0, size: 5, sort: 'id,asc' })
    await act(async () => resolvePrevious({ data: page([{ ...comment, id: 2, content: 'Earlier comment' }]) } as never))
    expect(await screen.findByText('Earlier comment')).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(4)
  })

  it('shows the empty state when the last comment on the first page is deleted', async () => {
    authenticate()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockResolvedValueOnce({ data: page([comment]) } as never)
      .mockResolvedValueOnce({ data: page([], 0, 0) } as never)
    vi.spyOn(commentsApi, 'deleteComment').mockResolvedValue({} as never)
    const user = userEvent.setup()
    renderDetail()
    await user.click(await screen.findByRole('button', { name: 'Delete comment 3' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete comment' }))
    expect(await screen.findByRole('heading', { name: 'No comments yet' })).toBeInTheDocument()
  })

  it('refetches the sorted page after creation and exposes the new last page', async () => {
    authenticate()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    const older = Array.from({ length: 5 }, (_, index) => ({ ...comment, id: index + 1, content: `Older ${index + 1}` }))
    const newest = { ...comment, id: 11, content: 'New comment' }
    const get = vi.spyOn(commentsApi, 'getComments')
      .mockResolvedValueOnce({ data: page(older) } as never)
      .mockResolvedValueOnce({ data: page(older, 0, 2) } as never)
      .mockResolvedValueOnce({ data: page([newest], 1, 2) } as never)
    vi.spyOn(commentsApi, 'createComment').mockResolvedValue({ data: newest } as never)
    const user = userEvent.setup()
    renderDetail()
    await screen.findByText('Older 1')
    fireEvent.change(await screen.findByLabelText('Create a comment'), { target: { value: 'New comment' } })
    await user.click(screen.getByRole('button', { name: 'Comment' }))
    await waitFor(() => expect(get).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('Older 5')).toBeInTheDocument()
    expect(screen.queryByText('New comment')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    expect(await screen.findByText('New comment')).toBeInTheDocument()
    expect(get).toHaveBeenNthCalledWith(2, 7, { page: 0, size: 5, sort: 'id,asc' })
    expect(get).toHaveBeenNthCalledWith(3, 7, { page: 1, size: 5, sort: 'id,asc' })
  })

  it('keeps the active comment page after deleting an item while that page remains valid', async () => {
    authenticate()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    const remaining = { ...comment, id: 4, content: 'Remaining page two comment' }
    const get = vi.spyOn(commentsApi, 'getComments')
      .mockResolvedValueOnce({ data: page(Array.from({ length: 5 }, (_, index) => ({ ...comment, id: index + 1 })), 0, 2) } as never)
      .mockResolvedValueOnce({ data: page([comment, remaining], 1, 2) } as never)
      .mockResolvedValueOnce({ data: page([remaining], 1, 2) } as never)
    vi.spyOn(commentsApi, 'deleteComment').mockResolvedValue({ status: 204 } as never)
    const user = userEvent.setup()
    renderDetail()

    await user.click(await screen.findByRole('button', { name: 'Next page' }))
    await screen.findByText('Comment three')
    await user.click(screen.getByRole('button', { name: 'Delete comment 3' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete comment' }))

    expect(await screen.findByText('Remaining page two comment')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Page 2', current: 'page' })).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(3)
    expect(get).toHaveBeenNthCalledWith(3, 7, { page: 1, size: 5, sort: 'id,asc' })
  })

  it('preserves an unfinished comment draft while navigating between pages', async () => {
    authenticate()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    const get = vi.spyOn(commentsApi, 'getComments')
      .mockResolvedValueOnce({ data: page([comment], 0, 2) } as never)
      .mockResolvedValueOnce({ data: page([{ ...comment, id: 4, content: 'Page two comment' }], 1, 2) } as never)
    const user = userEvent.setup()
    renderDetail()

    const draft = await screen.findByLabelText('Create a comment')
    await user.type(draft, 'Unfinished draft')
    await user.click(screen.getByRole('button', { name: 'Next page' }))

    expect(await screen.findByText('Page two comment')).toBeInTheDocument()
    expect(draft).toHaveValue('Unfinished draft')
    expect(get).toHaveBeenNthCalledWith(1, 7, { page: 0, size: 5, sort: 'id,asc' })
    expect(get).toHaveBeenNthCalledWith(2, 7, { page: 1, size: 5, sort: 'id,asc' })
  })

  it('does not let an older in-flight comment response overwrite the creation refetch', async () => {
    authenticate()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    let resolveOld!: (value: never) => void
    const get = vi.spyOn(commentsApi, 'getComments')
      .mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve }) as never)
      .mockResolvedValueOnce({ data: page([comment, { ...comment, id: 4, content: 'New comment' }]) } as never)
    vi.spyOn(commentsApi, 'createComment').mockResolvedValue({ data: { ...comment, id: 4, content: 'New comment' } } as never)
    const user = userEvent.setup()
    renderDetail()
    const input = await screen.findByLabelText('Create a comment')
    expect(screen.getByRole('status', { name: 'Loading comments' })).toBeInTheDocument()
    fireEvent.change(input, { target: { value: 'New comment' } })
    await user.click(screen.getByRole('button', { name: 'Comment' }))
    expect(await screen.findByText('New comment')).toBeInTheDocument()
    await act(async () => resolveOld({ data: page([comment]) } as never))
    expect(screen.getByText('New comment')).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('refetches the active comment page if navigation occurs while creation is pending', async () => {
    authenticate()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    const get = vi.spyOn(commentsApi, 'getComments')
      .mockResolvedValueOnce({ data: page([comment], 0, 2) } as never)
      .mockResolvedValueOnce({ data: page([{ ...comment, id: 4, content: 'Page two' }], 1, 2) } as never)
      .mockResolvedValueOnce({ data: page([{ ...comment, id: 4, content: 'Page two' }, { ...comment, id: 5, content: 'New comment' }], 1, 2) } as never)
    let resolveCreate!: (value: never) => void
    vi.spyOn(commentsApi, 'createComment').mockReturnValue(new Promise((resolve) => { resolveCreate = resolve }) as never)
    const user = userEvent.setup()
    renderDetail()
    await screen.findByText('Comment three')
    fireEvent.change(await screen.findByLabelText('Create a comment'), { target: { value: 'New comment' } })
    await user.click(screen.getByRole('button', { name: 'Comment' }))
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    await screen.findByText('Page two')
    await act(async () => resolveCreate({ data: { ...comment, id: 5, content: 'New comment' } } as never))
    expect(await screen.findByText('New comment')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Page 2', current: 'page' })).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(3)
    expect(get).toHaveBeenNthCalledWith(3, 7, { page: 1, size: 5, sort: 'id,asc' })
  })

  it('does not refetch comments when creation finishes after the page is left', async () => {
    authenticate()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    const get = vi.spyOn(commentsApi, 'getComments').mockResolvedValue({ data: page([comment]) } as never)
    let resolveCreate!: (value: never) => void
    vi.spyOn(commentsApi, 'createComment').mockReturnValue(new Promise((resolve) => { resolveCreate = resolve }) as never)
    const user = userEvent.setup()
    const rendered = renderDetail()
    fireEvent.change(await screen.findByLabelText('Create a comment'), { target: { value: 'New comment' } })
    await user.click(screen.getByRole('button', { name: 'Comment' }))
    rendered.unmount()
    await act(async () => resolveCreate({ data: { ...comment, id: 5, content: 'New comment' } } as never))
    expect(get).toHaveBeenCalledTimes(1)
  })

  it.each([403, 404])('shows a contextual %s comment-list error', async (status) => {
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockRejectedValue(failure(status, 'Unavailable'))
    renderDetail()
    expect(await screen.findByText(status === 403 ? 'Forbidden' : 'Comments not found')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it.each([403, 404])('keeps comment mutation %s errors in the dialog', async (status) => {
    authenticate()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockResolvedValue({ data: page([comment]) } as never)
    vi.spyOn(commentsApi, 'deleteComment').mockRejectedValue(failure(status, 'Cannot delete this comment'))
    const user = userEvent.setup()
    renderDetail()
    await user.click(await screen.findByRole('button', { name: 'Delete comment 3' }))
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Delete comment' }))
    const alert = await within(dialog).findByRole('alert')
    expect(alert).toHaveTextContent(status === 403 ? 'Forbidden' : 'Not found')
    expect(alert).toHaveTextContent('Cannot delete this comment')
    expect(screen.getByText('Comment three')).toBeInTheDocument()
  })

  it.each([403, 404])('shows comment creation %s errors without losing the form', async (status) => {
    authenticate()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockResolvedValue({ data: page([comment]) } as never)
    vi.spyOn(commentsApi, 'createComment').mockRejectedValue(failure(status, 'Cannot create comment'))
    const user = userEvent.setup()
    renderDetail()
    const input = await screen.findByLabelText('Create a comment')
    fireEvent.change(input, { target: { value: 'Draft comment' } })
    await user.click(screen.getByRole('button', { name: 'Comment' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(status === 403 ? 'Forbidden' : 'Not found')
    expect(alert).toHaveTextContent('Cannot create comment')
    expect(input).toHaveValue('Draft comment')
  })

  it.each([403, 404])('keeps comment edit %s errors in the dialog', async (status) => {
    authenticate()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockResolvedValue({ data: page([comment]) } as never)
    vi.spyOn(commentsApi, 'updateComment').mockRejectedValue(failure(status, 'Cannot edit comment'))
    const user = userEvent.setup()
    renderDetail()
    await user.click(await screen.findByRole('button', { name: 'Edit comment 3' }))
    const dialog = screen.getByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))
    const alert = await within(dialog).findByRole('alert')
    expect(alert).toHaveTextContent(status === 403 ? 'Forbidden' : 'Not found')
    expect(alert).toHaveTextContent('Cannot edit comment')
  })

  it('shows forbidden feedback for like actions', async () => {
    authenticate()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockResolvedValue({ data: page([comment]) } as never)
    vi.spyOn(likesApi, 'likeComment').mockRejectedValue(failure(403, 'Cannot like comment'))
    const user = userEvent.setup()
    renderDetail()
    await user.click(await screen.findByRole('button', { name: 'Like comment 3' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Forbidden')
    expect(alert).toHaveTextContent('Cannot like comment')
  })

  it('distinguishes a missing post from a missing like', async () => {
    authenticate()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockResolvedValue({ data: page([comment]) } as never)
    vi.spyOn(likesApi, 'unlikePost').mockRejectedValue(failure(404, 'Post not found'))
    vi.mocked(postsApi.getPostEngagement).mockResolvedValue({ data: { likeCount: 2, commentCount: 1, likedByMe: true } } as never)
    const user = userEvent.setup()
    renderDetail()
    await user.click(await screen.findByRole('button', { name: 'Unlike post 7' }))
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Not found')
    expect(alert).toHaveTextContent('Post not found')
  })
})
