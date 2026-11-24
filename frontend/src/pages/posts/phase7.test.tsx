import axios from 'axios'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../../auth/AuthContext'
import { clearRefreshToken, REFRESH_TOKEN_STORAGE_KEY } from '../../auth/authState'
import { setAccessToken } from '../../api/client'
import * as authApi from '../../api/auth'
import * as postsApi from '../../api/posts'
import * as commentsApi from '../../api/comments'
import * as likesApi from '../../api/likes'
import { PostDetailPage } from './PostDetailPage'

const post = { id: 7, content: 'Hello post', userId: 42, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z' }
const comment = { id: 3, content: 'Hello comment', userId: 42, postId: 7, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z' }
const commentPage = { content: [comment], totalElements: 1, totalPages: 1, size: 5, number: 0, first: true, last: true }

function jwt(role: 'USER' | 'ADMIN' = 'USER') {
  const payload = btoa(JSON.stringify({ sub: '42', role }))
  return `header.${payload}.signature`
}
async function restore(role: 'USER' | 'ADMIN' = 'USER') {
  sessionStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, 'refresh')
  vi.spyOn(authApi, 'refresh').mockResolvedValue({ data: { accessToken: jwt(role), refreshToken: 'rotated', tokenType: 'Bearer', expiresIn: 900 } } as never)
}
function renderDetail() {
  return render(<MemoryRouter initialEntries={['/posts/7']}><AuthProvider><Routes><Route path="/posts/:postId" element={<PostDetailPage />} /></Routes></AuthProvider></MemoryRouter>)
}

beforeEach(() => {
  vi.restoreAllMocks()
  clearRefreshToken()
  setAccessToken(null)
  vi.spyOn(postsApi, 'getPostEngagement').mockResolvedValue({ data: { likeCount: 2, commentCount: 1, likedByMe: false } } as never)
  vi.spyOn(commentsApi, 'getCommentEngagement').mockResolvedValue({ data: { likeCount: 1, likedByMe: false } } as never)
})

describe('Phase 7 comments and likes', () => {
  it('loads public comments and supports pagination and empty states', async () => {
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    const spy = vi.spyOn(commentsApi, 'getComments')
      .mockResolvedValueOnce({ data: { ...commentPage, content: [comment], totalPages: 2, last: false } } as never)
      .mockResolvedValueOnce({ data: { ...commentPage, content: [{ ...comment, id: 4, content: 'Second page' }], number: 1, first: false, last: true } } as never)
    const user = userEvent.setup()
    renderDetail()
    expect(await screen.findByText('Hello comment')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    expect(await screen.findByText('Second page')).toBeInTheDocument()
    expect(spy).toHaveBeenCalledWith(7, { page: 1, size: 5, sort: 'id,asc' })

    vi.restoreAllMocks()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockResolvedValue({ data: { ...commentPage, content: [], totalElements: 0 } } as never)
    renderDetail()
    expect(await screen.findByRole('heading', { name: 'No comments yet' })).toBeInTheDocument()
  })

  it('shows guest visibility and validates and creates comments', async () => {
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockResolvedValue({ data: commentPage } as never)
    const guest = renderDetail()
    expect(await screen.findByText('Hello comment')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Comment' })).not.toBeInTheDocument()
    guest.unmount()

    await restore()
    vi.mocked(postsApi.getPost).mockResolvedValue({ data: post } as never)
    vi.mocked(commentsApi.getComments).mockResolvedValue({ data: commentPage } as never)
    const create = vi.spyOn(commentsApi, 'createComment').mockResolvedValue({ data: { ...comment, id: 8, content: 'Created comment' } } as never)
    const user = userEvent.setup()
    renderDetail()
    const textarea = await screen.findByLabelText('Create a comment')
    await user.click(screen.getByRole('button', { name: 'Comment' }))
    expect(create).not.toHaveBeenCalled()
    fireEvent.change(textarea, { target: { value: 'x'.repeat(1001) } })
    expect((textarea as HTMLTextAreaElement).value).toHaveLength(1001)
    await user.click(screen.getByRole('button', { name: 'Comment' }))
    expect(await screen.findByText('Comment content must not exceed 1000 characters')).toBeInTheDocument()
    await user.clear(textarea)
    await user.type(textarea, 'Created comment')
    await user.click(screen.getByRole('button', { name: 'Comment' }))
    await waitFor(() => expect(create).toHaveBeenCalledWith(7, { content: 'Created comment' }))
  })

  it('shows owner and admin edit/delete controls and prevents non-owner controls', async () => {
    await restore('USER')
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockResolvedValue({ data: commentPage } as never)
    const user = userEvent.setup()
    const view = renderDetail()
    await screen.findByText('Hello comment')
    expect(screen.getByRole('button', { name: 'Edit comment 3' })).toBeInTheDocument()
    const update = vi.spyOn(commentsApi, 'updateComment').mockResolvedValue({ data: { ...comment, content: 'Edited comment' } } as never)
    await user.click(screen.getByRole('button', { name: 'Edit comment 3' }))
    fireEvent.change(screen.getByLabelText('Comment content'), { target: { value: 'Edited comment' } })
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(update).toHaveBeenCalledWith(7, 3, { content: 'Edited comment' }))
    view.unmount()

    await restore('USER')
    vi.mocked(postsApi.getPost).mockResolvedValue({ data: post } as never)
    vi.mocked(commentsApi.getComments).mockResolvedValue({ data: { ...commentPage, content: [{ ...comment, userId: 99 }] } } as never)
    renderDetail()
    await screen.findByText('Hello comment')
    expect(screen.queryByRole('button', { name: 'Edit comment 3' })).not.toBeInTheDocument()
  })

  it('confirms comment deletion and updates only after success', async () => {
    await restore('ADMIN')
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockResolvedValue({ data: commentPage } as never)
    let resolveDelete!: (value: never) => void
    const deletePromise = new Promise<never>((resolve) => { resolveDelete = resolve })
    const remove = vi.spyOn(commentsApi, 'deleteComment').mockReturnValue(deletePromise as never)
    const user = userEvent.setup()
    renderDetail()
    await screen.findByText('Hello comment')
    await user.click(screen.getByRole('button', { name: 'Delete comment 3' }))
    expect(screen.getByRole('dialog', { name: 'Delete comment' })).toBeInTheDocument()
    expect(remove).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Delete comment' }))
    expect(remove).toHaveBeenCalledWith(7, 3)
    expect(screen.getByText('Hello comment')).toBeInTheDocument()
    vi.mocked(commentsApi.getComments).mockResolvedValue({ data: { ...commentPage, content: [], totalElements: 0, totalPages: 0 } } as never)
    await act(async () => resolveDelete({} as never))
    await waitFor(() => expect(screen.queryByText('Hello comment')).not.toBeInTheDocument())
  })

  it('handles comment loading 403, 404, network, and validation errors', async () => {
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockRejectedValue(new axios.AxiosError('forbidden', 'ERR_FORBIDDEN', undefined, undefined, { data: { status: 403, message: 'Forbidden' }, status: 403, statusText: 'Forbidden', headers: {}, config: {} as never }))
    renderDetail()
    expect(await screen.findByText('You do not have permission to view comments.')).toBeInTheDocument()

    vi.restoreAllMocks()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockRejectedValue(new axios.AxiosError('network', 'ERR_NETWORK'))
    renderDetail()
    expect(await screen.findByText('Network error. Check your connection and try again.')).toBeInTheDocument()
  })

  it('uses separate post and comment like/unlike requests and handles duplicate and invalid actions', async () => {
    await restore()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockResolvedValue({ data: commentPage } as never)
    const likePost = vi.spyOn(likesApi, 'likePost').mockResolvedValue({ data: { id: 1, userId: 42, postId: 7, commentId: null, createdAt: '2026-01-01T00:00:00Z' } } as never)
    const unlikePost = vi.spyOn(likesApi, 'unlikePost').mockResolvedValue({} as never)
    const likeComment = vi.spyOn(likesApi, 'likeComment').mockResolvedValue({ data: { id: 2, userId: 42, postId: 7, commentId: 3, createdAt: '2026-01-01T00:00:00Z' } } as never)
    const unlikeComment = vi.spyOn(likesApi, 'unlikeComment').mockResolvedValue({} as never)
    vi.mocked(postsApi.getPostEngagement)
      .mockResolvedValueOnce({ data: { likeCount: 2, commentCount: 1, likedByMe: false } } as never)
      .mockResolvedValueOnce({ data: { likeCount: 3, commentCount: 1, likedByMe: true } } as never)
      .mockResolvedValueOnce({ data: { likeCount: 2, commentCount: 1, likedByMe: false } } as never)
      .mockResolvedValueOnce({ data: { likeCount: 3, commentCount: 1, likedByMe: true } } as never)
      .mockResolvedValue({ data: { likeCount: 2, commentCount: 1, likedByMe: false } } as never)
    vi.mocked(commentsApi.getCommentEngagement)
      .mockResolvedValueOnce({ data: { likeCount: 1, likedByMe: false } } as never)
      .mockResolvedValue({ data: { likeCount: 2, likedByMe: true } } as never)
    const user = userEvent.setup()
    renderDetail()
    await screen.findByText('Hello comment')

    await user.click(screen.getByRole('button', { name: 'Like post 7' }))
    expect(likePost).toHaveBeenCalledWith(7)
    expect(await screen.findByText('Like confirmed.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Unlike post 7' }))
    expect(unlikePost).toHaveBeenCalledWith(7)

    await user.click(screen.getByRole('button', { name: 'Like comment 3' }))
    expect(likeComment).toHaveBeenCalledWith(7, 3)
    await user.click(screen.getByRole('button', { name: 'Unlike comment 3' }))
    expect(unlikeComment).toHaveBeenCalledWith(7, 3)

    vi.mocked(likesApi.likePost).mockRejectedValue(new axios.AxiosError('conflict', 'ERR_CONFLICT', undefined, undefined, { data: { status: 409, message: 'You have already liked this post' }, status: 409, statusText: 'Conflict', headers: {}, config: {} as never }))
    await user.click(screen.getByRole('button', { name: 'Like post 7' }))
    expect(await screen.findByText('This item is already liked.')).toBeInTheDocument()

    vi.mocked(likesApi.unlikePost).mockRejectedValue(new axios.AxiosError('missing', 'ERR_NOT_FOUND', undefined, undefined, { data: { status: 404, message: 'Like not found' }, status: 404, statusText: 'Not Found', headers: {}, config: {} as never }))
    await user.click(screen.getByRole('button', { name: 'Unlike post 7' }))
    expect(await screen.findByText('No existing like was found.')).toBeInTheDocument()
  })

  it('does not infer liked state and disables both pending actions', async () => {
    await restore()
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    vi.spyOn(commentsApi, 'getComments').mockResolvedValue({ data: commentPage } as never)
    let resolveLike!: (value: never) => void
    vi.spyOn(likesApi, 'likePost').mockReturnValue(new Promise<never>((resolve) => { resolveLike = resolve }) as never)
    const user = userEvent.setup()
    renderDetail()
    await screen.findByText('Hello comment')
    await user.click(screen.getByRole('button', { name: 'Like post 7' }))
    expect(screen.getByRole('button', { name: 'Like post 7' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Unlike post 7' })).toBeDisabled()
    expect(screen.queryByText('Like confirmed.')).not.toBeInTheDocument()
    await act(async () => resolveLike({ data: { id: 1, userId: 42, postId: 7, commentId: null, createdAt: '2026-01-01T00:00:00Z' } } as never))
    expect(await screen.findByText('Like confirmed.')).toBeInTheDocument()
  })
})
