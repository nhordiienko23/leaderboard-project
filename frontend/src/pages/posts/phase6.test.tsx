import axios from 'axios'
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../../auth/AuthContext'
import { clearRefreshToken, REFRESH_TOKEN_STORAGE_KEY } from '../../auth/authState'
import { setAccessToken } from '../../api/client'
import * as postsApi from '../../api/posts'
import * as authApi from '../../api/auth'
import { PostsPage } from './PostsPage'
import { PostDetailPage } from './PostDetailPage'

const post = { id: 7, content: 'Hello post', userId: 42, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-02T00:00:00Z' }
const page = { content: [post], totalElements: 1, totalPages: 1, size: 5, number: 0, first: true, last: true }

function jwt(role: 'USER' | 'ADMIN' = 'USER') {
  const payload = btoa(JSON.stringify({ sub: '42', role }))
  return `header.${payload}.signature`
}

async function restore(role: 'USER' | 'ADMIN' = 'USER') {
  sessionStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, 'refresh')
  vi.spyOn(authApi, 'refresh').mockResolvedValue({ data: { accessToken: jwt(role), refreshToken: 'rotated', tokenType: 'Bearer', expiresIn: 900 } } as never)
}

function renderFeed() {
  return render(<MemoryRouter><AuthProvider><PostsPage /></AuthProvider></MemoryRouter>)
}

beforeEach(() => {
  vi.restoreAllMocks()
  clearRefreshToken()
  setAccessToken(null)
  vi.spyOn(postsApi, 'getPostEngagement').mockResolvedValue({ data: { likeCount: 2, commentCount: 1, likedByMe: false } } as never)
})

describe('Phase 6 posts', () => {
  it('renders loading, feed data, and guest composer visibility', async () => {
    vi.spyOn(postsApi, 'getPosts').mockResolvedValue({ data: page } as never)
    renderFeed()
    expect(screen.getByRole('status', { name: 'Loading posts' })).toBeInTheDocument()
    expect(await screen.findByText('Hello post')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Post' })).not.toBeInTheDocument()
  })

  it('renders an empty feed', async () => {
    vi.spyOn(postsApi, 'getPosts').mockResolvedValue({ data: { ...page, content: [], totalElements: 0 } } as never)
    renderFeed()
    expect(await screen.findByRole('heading', { name: 'No posts yet' })).toBeInTheDocument()
  })

  it('paginates using Spring page fields', async () => {
    const spy = vi.spyOn(postsApi, 'getPosts')
      .mockResolvedValueOnce({ data: { ...page, content: [{ ...post, id: 1 }], first: true, last: false, totalPages: 2, number: 0 } } as never)
      .mockResolvedValueOnce({ data: { ...page, content: [{ ...post, id: 2 }], first: false, last: true, totalPages: 2, number: 1 } } as never)
    const user = userEvent.setup()
    renderFeed()
    await screen.findByText('Hello post')
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    expect(await screen.findByRole('button', { name: 'Page 2', current: 'page' })).toBeInTheDocument()
    expect(spy).toHaveBeenCalledWith({ page: 1, size: 5, sort: 'id,desc' })
  })

  it('shows composer only when authenticated and validates/creates posts', async () => {
    await restore()
    vi.spyOn(postsApi, 'getPosts').mockResolvedValue({ data: page } as never)
    const create = vi.spyOn(postsApi, 'createPost').mockResolvedValue({ data: { ...post, id: 8, content: 'Created' } } as never)
    const user = userEvent.setup()
    renderFeed()
    const textarea = await screen.findByLabelText('Create a post')
    await user.click(screen.getByRole('button', { name: 'Post' }))
    expect(create).not.toHaveBeenCalled()
    await user.type(textarea, 'x'.repeat(256))
    expect((textarea as HTMLTextAreaElement).value).toHaveLength(255)
    await user.clear(textarea)
    await user.type(textarea, 'Created')
    await user.click(screen.getByRole('button', { name: 'Post' }))
    await waitFor(() => expect(create).toHaveBeenCalledWith({ content: 'Created' }))
  })

  it('reloads page zero after post creation without locally prepending the result', async () => {
    await restore()
    const firstPagePosts = Array.from({ length: 5 }, (_, index) => ({
      ...post,
      id: index + 1,
      content: `Page zero post ${index + 1}`,
    }))
    const secondPagePost = { ...post, id: 6, content: 'Page one post' }
    let resolveRefresh!: (value: never) => void
    const get = vi.spyOn(postsApi, 'getPosts')
      .mockResolvedValueOnce({ data: { ...page, content: firstPagePosts, totalElements: 6, totalPages: 2, last: false } } as never)
      .mockResolvedValueOnce({ data: { ...page, content: [secondPagePost], totalElements: 6, totalPages: 2, number: 1, first: false } } as never)
      .mockResolvedValueOnce({ data: { ...page, content: firstPagePosts, totalElements: 6, totalPages: 2, last: false } } as never)
      .mockReturnValueOnce(new Promise((resolve) => { resolveRefresh = resolve }) as never)
    vi.spyOn(postsApi, 'createPost').mockResolvedValue({ data: { ...post, id: 8, content: 'Created' } } as never)
    const user = userEvent.setup()
    renderFeed()

    await user.click(await screen.findByRole('button', { name: 'Next page' }))
    expect(await screen.findByText('Page one post')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Previous page' }))
    expect(await screen.findByText('Page zero post 1')).toBeInTheDocument()
    const input = screen.getByLabelText('Create a post')
    await user.type(input, 'Created')
    await user.click(screen.getByRole('button', { name: 'Post' }))

    expect(await screen.findByRole('status', { name: 'Loading posts' })).toBeInTheDocument()
    expect(screen.queryAllByRole('article')).toHaveLength(0)
    await act(async () => resolveRefresh({
      data: { ...page, content: [{ ...post, id: 8, content: 'Created' }, ...firstPagePosts.slice(0, 4)], totalElements: 7, totalPages: 2, last: false },
    } as never))

    expect(await screen.findByText('Created')).toBeInTheDocument()
    expect(screen.queryByText('Page one post')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Page 1', current: 'page' })).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(5)
    expect(get).toHaveBeenNthCalledWith(1, { page: 0, size: 5, sort: 'id,desc' })
    expect(get).toHaveBeenNthCalledWith(2, { page: 1, size: 5, sort: 'id,desc' })
    expect(get).toHaveBeenNthCalledWith(3, { page: 0, size: 5, sort: 'id,desc' })
    expect(get).toHaveBeenNthCalledWith(4, { page: 0, size: 5, sort: 'id,desc' })
  })

  it('loads post detail and handles invalid and missing IDs', async () => {
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    const detail = render(<MemoryRouter initialEntries={["/posts/7"]}><AuthProvider><Routes><Route path="/posts/:postId" element={<PostDetailPage />} /></Routes></AuthProvider></MemoryRouter>)
    expect(await screen.findByRole('heading', { name: 'Post #7' })).toBeInTheDocument()
    detail.unmount()

    vi.mocked(postsApi.getPost).mockRejectedValue(new axios.AxiosError('missing', 'ERR_NOT_FOUND', undefined, undefined, { data: { status: 404, message: 'Post not found' }, status: 404, statusText: 'Not Found', headers: {}, config: {} as never }))
    render(<MemoryRouter initialEntries={["/posts/404"]}><AuthProvider><Routes><Route path="/posts/:postId" element={<PostDetailPage />} /></Routes></AuthProvider></MemoryRouter>)
    expect(await screen.findByText('Post not found')).toBeInTheDocument()

    render(<MemoryRouter initialEntries={["/posts/nope"]}><AuthProvider><Routes><Route path="/posts/:postId" element={<PostDetailPage />} /></Routes></AuthProvider></MemoryRouter>)
    expect(await screen.findByText('Invalid post ID.')).toBeInTheDocument()
  })

  it('shows owner and admin controls and updates only after success', async () => {
    await restore('ADMIN')
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: post } as never)
    let resolveUpdate!: (value: never) => void
    const updatePromise = new Promise<never>((resolve) => { resolveUpdate = resolve })
    const update = vi.spyOn(postsApi, 'updatePost').mockReturnValue(updatePromise as never)
    const user = userEvent.setup()
    render(<MemoryRouter initialEntries={["/posts/7"]}><AuthProvider><Routes><Route path="/posts/:postId" element={<PostDetailPage />} /></Routes></AuthProvider></MemoryRouter>)
    await screen.findByText('Hello post')
    await user.click(screen.getByRole('button', { name: 'Edit post 7' }))
    const textarea = screen.getByLabelText('Post content')
    fireEvent.change(textarea, { target: { value: 'Edited' } })
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    expect(update).toHaveBeenCalledWith(7, { content: 'Edited' })
    expect(screen.getByText('Hello post')).toBeInTheDocument()
    await act(async () => resolveUpdate({ data: { ...post, content: 'Edited' } } as never))
    expect(await screen.findByText('Edited')).toBeInTheDocument()
  })

  it('restricts controls for non-owner and confirms delete', async () => {
    await restore('USER')
    vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: { ...post, userId: 99 } } as never)
    const view = render(<MemoryRouter initialEntries={["/posts/7"]}><AuthProvider><Routes><Route path="/posts/:postId" element={<PostDetailPage />} /></Routes></AuthProvider></MemoryRouter>)
    await screen.findByText('Hello post')
    expect(screen.queryByRole('button', { name: 'Delete post 7' })).not.toBeInTheDocument()
    view.unmount()

    await restore('USER')
    vi.mocked(postsApi.getPost).mockResolvedValue({ data: post } as never)
    vi.spyOn(postsApi, 'deletePost').mockResolvedValue({} as never)
    render(<MemoryRouter initialEntries={["/posts/7"]}><AuthProvider><Routes><Route path="/posts/:postId" element={<PostDetailPage />} /><Route path="/posts" element={<div>Posts route</div>} /></Routes></AuthProvider></MemoryRouter>)
    await screen.findByText('Hello post')
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Delete post 7' }))
    expect(screen.getByRole('dialog', { name: 'Delete post' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Delete post' }))
    await waitFor(() => expect(screen.getByText('Posts route')).toBeInTheDocument())
  })

  it('handles network and 403 errors', async () => {
    vi.spyOn(postsApi, 'getPosts').mockRejectedValue(new axios.AxiosError('network', 'ERR_NETWORK'))
    renderFeed()
    expect(await screen.findByText('Network error. Check your connection and try again.')).toBeInTheDocument()

    vi.restoreAllMocks()
    vi.spyOn(postsApi, 'getPost').mockRejectedValue(new axios.AxiosError('forbidden', 'ERR_FORBIDDEN', undefined, undefined, { data: { status: 403, message: 'Forbidden' }, status: 403, statusText: 'Forbidden', headers: {}, config: {} as never }))
    render(<MemoryRouter initialEntries={["/posts/7"]}><AuthProvider><Routes><Route path="/posts/:postId" element={<PostDetailPage />} /></Routes></AuthProvider></MemoryRouter>)
    expect(await screen.findByText('Forbidden')).toBeInTheDocument()
  })
})
