import { act, render, screen, waitFor, within } from '@testing-library/react'
import { StrictMode } from 'react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../api/client'
import * as adminApi from '../api/admin'
import { CommentList } from '../features/comments/CommentList'
import { PostsPage } from './posts/PostsPage'
import { UserDirectoryPage } from './users/UserDirectoryPage'
import { AdminDashboardPage } from './admin/AdminDashboardPage'

let isAuthenticated = false
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ isAuthenticated }) }))
vi.mock('../features/posts/PostComposer', () => ({
  PostComposer: ({ onCreated }: { onCreated: (post: { id: number }) => void }) =>
    <button onClick={() => onCreated({ id: 99 })}>Simulate post creation</button>,
}))
vi.mock('../features/comments/CommentComposer', () => ({
  CommentComposer: ({ onCreated }: { onCreated: () => void }) =>
    <button onClick={onCreated}>Simulate comment creation</button>,
}))
vi.mock('../features/posts/PostCard', () => ({
  PostCard: ({ post, onDeleted }: { post: { content: string }, onDeleted: () => void }) =>
    <div>{post.content}<button onClick={onDeleted}>Simulate deletion</button></div>,
}))
vi.mock('../features/comments/CommentItem', () => ({
  CommentItem: ({ comment, onDeleted }: { comment: { content: string }, onDeleted: () => void }) =>
    <div>{comment.content}<button onClick={onDeleted}>Simulate deletion</button></div>,
}))

const user = {
  id: 1, username: 'Ada', email: 'ada@example.com', role: 'USER',
  registeredAt: '2024-01-02T10:00:00Z', lastLoginAt: null, updatedAt: '2024-01-02T10:00:00Z',
}
const nested = (number: number) => ({
  content: [{ id: number + 1, content: `Item ${number + 1}` }],
  page: { number, size: 5, totalElements: 6, totalPages: 2 },
})
const flat = (number: number) => ({
  content: [{ ...user, id: number + 1, username: number ? 'Grace' : 'Ada' }],
  page: number, size: 20, totalElements: 21, totalPages: 2, first: number === 0, last: number === 1,
})

function responseFor(kind: string, number: number, totalPages = 5) {
  const isUser = kind === 'directory' || kind === 'admin'
  const size = isUser ? 20 : 5
  const content = totalPages === 0 ? [] : isUser
    ? [{ ...user, id: number + 1, username: `Member ${number + 1}` }]
    : [{ id: number + 1, content: `Item ${number + 1}` }]
  const totalElements = totalPages === 0 ? 0 : (totalPages - 1) * size + 1
  return isUser
    ? { content, page: number, size, totalElements, totalPages, first: number === 0, last: number >= totalPages - 1 }
    : { content, page: { number, size, totalElements, totalPages } }
}

beforeEach(() => {
  vi.restoreAllMocks()
  isAuthenticated = false
})

describe('pagination at the component API boundary', () => {
  const consumers = [
    ['posts', <PostsPage />, '/api/posts', { size: 5, sort: 'id,desc' }],
    ['comments', <CommentList postId={7} />, '/api/posts/7/comments', { size: 5, sort: 'id,asc' }],
    ['directory', <UserDirectoryPage />, '/api/users', { size: 20 }],
    ['admin', <AdminDashboardPage />, '/api/users', { size: 20 }],
  ] as const

  it.each(consumers)('routes every %s navigation through its existing API', async (kind, component, url, params) => {
    const get = vi.spyOn(apiClient, 'get')
    for (const number of [0, 1, 3, 2, 4, 0, 2, 3]) {
      get.mockResolvedValueOnce({ data: responseFor(kind, number) } as never)
    }
    const userAction = userEvent.setup()
    render(<MemoryRouter>{component}</MemoryRouter>)

    await screen.findByRole('button', { name: 'Page 1', current: 'page' })
    expect(screen.getByRole('button', { name: 'First page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    expect(get).toHaveBeenLastCalledWith(url, { params: { page: 0, ...params } })

    const go = async (label: string, target: number) => {
      await userAction.click(screen.getByRole('button', { name: label }))
      expect(await screen.findByRole('button', { name: `Page ${target + 1}`, current: 'page' })).toBeInTheDocument()
      expect(get).toHaveBeenLastCalledWith(url, { params: { page: target, ...params } })
    }

    await go('Next page', 1)
    await go('Page 4', 3)
    await go('Previous page', 2)
    await go('Last page', 4)
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Last page' })).toBeDisabled()
    await go('First page', 0)

    const input = screen.getByRole('textbox', { name: 'Go to page' })
    const beforeInvalid = get.mock.calls.length
    await userAction.clear(input)
    await userAction.type(input, '6{Enter}')
    expect(screen.getByRole('alert')).toHaveTextContent('between 1 and 5')
    expect(get).toHaveBeenCalledTimes(beforeInvalid)
    await userAction.clear(input)
    await userAction.type(input, '3{Enter}')
    expect(await screen.findByRole('button', { name: 'Page 3', current: 'page' })).toBeInTheDocument()
    expect(get).toHaveBeenLastCalledWith(url, { params: { page: 2, ...params } })
    const beforeSamePage = get.mock.calls.length
    await userAction.click(screen.getByRole('button', { name: 'Page 3' }))
    await userAction.click(screen.getByRole('button', { name: 'Go' }))
    expect(get).toHaveBeenCalledTimes(beforeSamePage)
    const currentInput = screen.getByRole('textbox', { name: 'Go to page' })
    await userAction.clear(currentInput)
    await userAction.type(currentInput, '4')
    await userAction.click(screen.getByRole('button', { name: 'Go' }))
    expect(await screen.findByRole('button', { name: 'Page 4', current: 'page' })).toBeInTheDocument()
    expect(get).toHaveBeenLastCalledWith(url, { params: { page: 3, ...params } })
  })

  it.each(consumers)('hides %s pagination for empty and single-page results', async (kind, component) => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: responseFor(kind, 0, 0) } as never)
    const view = render(<MemoryRouter>{component}</MemoryRouter>)
    await screen.findByRole('heading', { name: kind === 'posts' ? 'No posts yet' : kind === 'comments' ? 'No comments yet' : 'No users found' })
    expect(screen.queryByRole('navigation', { name: 'Pagination' })).not.toBeInTheDocument()
    view.unmount()
    get.mockResolvedValue({ data: responseFor(kind, 0, 1) } as never)
    render(<MemoryRouter>{component}</MemoryRouter>)
    await screen.findByText(kind === 'directory' || kind === 'admin' ? 'Member 1' : 'Item 1')
    expect(screen.queryByRole('navigation', { name: 'Pagination' })).not.toBeInTheDocument()
  })

  it.each([
    ['posts', <PostsPage />, '/api/posts', 'id,desc'],
    ['comments', <CommentList postId={7} />, '/api/posts/7/comments', 'id,asc'],
  ])('renders and navigates normalized %s metadata from nested responses', async (_label, component, url, sort) => {
    const get = vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ data: nested(0) } as never)
      .mockResolvedValueOnce({ data: nested(1) } as never)
    render(<MemoryRouter>{component}</MemoryRouter>)

    expect(await screen.findByText('Item 1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Page 1' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }))
    expect(await screen.findByText('Item 2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
    expect(get).toHaveBeenNthCalledWith(2, url, { params: { page: 1, size: 5, sort } })
  })

  it.each([
    ['posts', <PostsPage />, '/api/posts', 'id,desc', 'No posts yet'],
    ['comments', <CommentList postId={7} />, '/api/posts/7/comments', 'id,asc', 'No comments yet'],
  ])('preserves %s page-shrink recovery after an out-of-range empty response', async (_label, component, url, sort, emptyTitle) => {
    const empty = (number: number) => ({
      content: [], page: { number, size: 5, totalElements: 0, totalPages: 0 },
    })
    const get = vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ data: nested(0) } as never)
      .mockResolvedValueOnce({ data: nested(1) } as never)
      .mockResolvedValueOnce({ data: empty(1) } as never)
      .mockResolvedValueOnce({ data: empty(0) } as never)
    render(<MemoryRouter>{component}</MemoryRouter>)
    await screen.findByText('Item 1')
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }))
    await screen.findByText('Item 2')
    await userEvent.click(screen.getByRole('button', { name: 'Simulate deletion' }))
    expect(await screen.findByRole('heading', { name: emptyTitle })).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(4)
    expect(get).toHaveBeenNthCalledWith(3, url, { params: { page: 1, size: 5, sort } })
    expect(get).toHaveBeenNthCalledWith(4, url, { params: { page: 0, size: 5, sort } })
  })

  it.each([
    ['posts', <PostsPage />, '/api/posts', 'id,desc'],
    ['comments', <CommentList postId={7} />, '/api/posts/7/comments', 'id,asc'],
  ])('moves %s directly to the latest last valid page after shrinkage', async (kind, component, url, sort) => {
    const get = vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ data: responseFor(kind, 0) } as never)
      .mockResolvedValueOnce({ data: responseFor(kind, 4) } as never)
      .mockResolvedValueOnce({ data: { ...responseFor(kind, 4, 2), content: [] } } as never)
      .mockResolvedValueOnce({ data: responseFor(kind, 1, 2) } as never)
    render(<MemoryRouter>{component}</MemoryRouter>)
    await screen.findByText('Item 1')
    await userEvent.click(screen.getByRole('button', { name: 'Last page' }))
    await screen.findByText('Item 5')
    await userEvent.click(screen.getByRole('button', { name: 'Simulate deletion' }))
    expect(await screen.findByText('Item 2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Page 2', current: 'page' })).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(4)
    expect(get).toHaveBeenNthCalledWith(3, url, { params: { page: 4, size: 5, sort } })
    expect(get).toHaveBeenNthCalledWith(4, url, { params: { page: 1, size: 5, sort } })
  })

  it.each([
    ['posts', <PostsPage />, 'Simulate post creation', 0],
    ['comments', <CommentList postId={7} />, 'Simulate comment creation', 4],
  ])('ignores a stale %s page response after creation triggers a newer load', async (kind, component, createLabel, target) => {
    isAuthenticated = true
    let resolveOld!: (value: never) => void
    const get = vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ data: responseFor(kind, 0) } as never)
      .mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve }) as never)
      .mockResolvedValueOnce({ data: responseFor(kind, target) } as never)
    render(<MemoryRouter>{component}</MemoryRouter>)
    await screen.findByText('Item 1')
    await userEvent.click(screen.getByRole('button', { name: 'Last page' }))
    await waitFor(() => expect(get).toHaveBeenCalledTimes(2))
    await userEvent.click(screen.getByRole('button', { name: createLabel }))
    expect(await screen.findByRole('button', { name: `Page ${target + 1}`, current: 'page' })).toBeInTheDocument()
    await act(async () => resolveOld({ data: { ...responseFor(kind, 4, 2), content: [] } } as never))
    expect(screen.getByText(`Item ${target + 1}`)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: `Page ${target + 1}`, current: 'page' })).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(3)
  })

  it('handles admin deletion shrinking the final page to zero pages without repeated requests', async () => {
    const get = vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ data: responseFor('admin', 0) } as never)
      .mockResolvedValueOnce({ data: responseFor('admin', 4) } as never)
      .mockResolvedValueOnce({ data: responseFor('admin', 4, 0) } as never)
      .mockResolvedValueOnce({ data: responseFor('admin', 0, 0) } as never)
    const remove = vi.spyOn(adminApi, 'deleteAdminUser').mockResolvedValue({} as never)
    const userAction = userEvent.setup()
    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>)
    await screen.findByText('Member 1')
    await userAction.click(screen.getByRole('button', { name: 'Last page' }))
    await screen.findByText('Member 5')
    await userAction.click(screen.getByRole('button', { name: 'Delete Member 5' }))
    await userAction.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete user' }))
    expect(await screen.findByRole('heading', { name: 'No users found' })).toBeInTheDocument()
    expect(remove).toHaveBeenCalledWith(5)
    expect(get).toHaveBeenCalledTimes(4)
    expect(get).toHaveBeenNthCalledWith(3, '/api/users', { params: { page: 4, size: 20 } })
    expect(get).toHaveBeenNthCalledWith(4, '/api/users', { params: { page: 0, size: 20 } })
    expect(screen.queryByRole('navigation', { name: 'Pagination' })).not.toBeInTheDocument()
  })

  it('moves admin directly to the new last page when several pages disappear', async () => {
    const get = vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ data: responseFor('admin', 0) } as never)
      .mockResolvedValueOnce({ data: responseFor('admin', 4) } as never)
      .mockResolvedValueOnce({ data: { ...responseFor('admin', 4, 2), content: [] } } as never)
      .mockResolvedValueOnce({ data: responseFor('admin', 1, 2) } as never)
    vi.spyOn(adminApi, 'deleteAdminUser').mockResolvedValue({} as never)
    const userAction = userEvent.setup()
    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>)
    await screen.findByText('Member 1')
    await userAction.click(screen.getByRole('button', { name: 'Last page' }))
    await screen.findByText('Member 5')
    await userAction.click(screen.getByRole('button', { name: 'Delete Member 5' }))
    await userAction.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete user' }))
    expect(await screen.findByText('Member 2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Page 2', current: 'page' })).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(4)
    expect(get).toHaveBeenNthCalledWith(4, '/api/users', { params: { page: 1, size: 20 } })
  })

  it('ignores an older directory request after a newer request succeeds', async () => {
    let resolveOld!: (value: never) => void
    const get = vi.spyOn(apiClient, 'get')
      .mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve }) as never)
      .mockResolvedValueOnce({ data: responseFor('directory', 0) } as never)
    render(<StrictMode><MemoryRouter><UserDirectoryPage /></MemoryRouter></StrictMode>)
    expect(await screen.findByText('Member 1')).toBeInTheDocument()
    await act(async () => resolveOld({ data: responseFor('directory', 0, 0) } as never))
    expect(screen.getByText('Member 1')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'No users found' })).not.toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('ignores a stale admin shrink response after a newer reload of the selected page', async () => {
    let resolveDelete!: (value: never) => void
    let resolveOld!: (value: never) => void
    const get = vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ data: responseFor('admin', 0) } as never)
      .mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve }) as never)
      .mockResolvedValueOnce({ data: responseFor('admin', 4) } as never)
    vi.spyOn(adminApi, 'deleteAdminUser').mockReturnValue(new Promise((resolve) => { resolveDelete = resolve }) as never)
    const userAction = userEvent.setup()
    render(<MemoryRouter><AdminDashboardPage /></MemoryRouter>)
    await screen.findByText('Member 1')
    await userAction.click(screen.getByRole('button', { name: 'Delete Member 1' }))
    await userAction.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete user' }))
    await userAction.click(screen.getByRole('button', { name: 'Last page' }))
    await waitFor(() => expect(get).toHaveBeenCalledTimes(2))
    await act(async () => resolveDelete({ status: 204 } as never))
    expect(await screen.findByText('Member 5')).toBeInTheDocument()
    await act(async () => resolveOld({ data: { ...responseFor('admin', 4, 2), content: [] } } as never))
    expect(screen.getByText('Member 5')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Page 5', current: 'page' })).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(3)
  })

  it.each([
    ['posts', <PostsPage />, 'Unable to load posts'],
    ['comments', <CommentList postId={7} />, 'Unable to load comments'],
    ['directory', <UserDirectoryPage />, 'Unable to load users'],
    ['admin', <AdminDashboardPage />, 'Unable to load users'],
  ])('sends malformed %s responses to existing load-error handling', async (_label, component, title) => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { content: [], page: {} } } as never)
    render(<MemoryRouter>{component}</MemoryRouter>)
    expect(await screen.findByText(title)).toBeInTheDocument()
    expect(screen.getByText('Invalid pagination response.')).toBeInTheDocument()
    expect(screen.queryByText(/NaN/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it.each([
    ['directory', <UserDirectoryPage />, 'members'],
    ['admin', <AdminDashboardPage />, 'users'],
  ])('keeps %s compatible with flat user responses', async (_label, component, noun) => {
    const get = vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ data: flat(0) } as never)
      .mockResolvedValueOnce({ data: flat(1) } as never)
    render(<MemoryRouter>{component}</MemoryRouter>)

    expect(await screen.findByText(`Page 1 of 2 · 21 ${noun}`)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }))
    expect(await screen.findByText(`Page 2 of 2 · 21 ${noun}`)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
    expect(get).toHaveBeenNthCalledWith(2, '/api/users', { params: { page: 1, size: 20 } })
  })
})
