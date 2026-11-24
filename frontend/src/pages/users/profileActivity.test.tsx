import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as users from '../../api/users'
import * as posts from '../../api/posts'
import { apiClient } from '../../api/client'
import type { ProfileActivityResponse, UserResponse } from '../../api/types'
import { UserProfilePage } from './UserProfilePage'

const user: UserResponse = {
  id: 1, username: 'Ada', email: 'ada@example.com', role: 'USER',
  registeredAt: '2024-01-01', lastLoginAt: null, updatedAt: '2024-01-01',
}
const counts: ProfileActivityResponse = {
  postsCreated: 1, commentsAuthored: 2, postLikesGiven: 3,
  commentLikesGiven: 4, postLikesReceived: 5, commentLikesReceived: 6,
}
const zero: ProfileActivityResponse = {
  postsCreated: 0, commentsAuthored: 0, postLikesGiven: 0,
  commentLikesGiven: 0, postLikesReceived: 0, commentLikesReceived: 0,
}

function renderProfile() {
  render(<MemoryRouter initialEntries={['/users/1']}>
    <Link to="/users/2">Next user</Link>
    <Routes><Route path="/users/:id" element={<UserProfilePage />} /></Routes>
  </MemoryRouter>)
}

beforeEach(() => {
  vi.restoreAllMocks()
  vi.spyOn(users, 'getUser').mockImplementation(async (id) => ({
    data: id === 1 ? user : { ...user, id: 2, username: 'Grace' },
  }) as never)
})

describe('profile activity', () => {
  it('shows all nonzero metrics after personal information', async () => {
    vi.spyOn(posts, 'getProfileActivity').mockResolvedValue({ data: counts } as never)
    renderProfile()
    const section = await screen.findByRole('region', { name: 'Profile activity' })
    await within(section).findByText('Posts')
    for (const [label, value] of Object.entries({
      Posts: 1, Comments: 2, 'Post likes given': 3, 'Comment likes given': 4,
      'Post likes received': 5, 'Comment likes received': 6,
    })) {
      expect(within(section).getByText(label).nextElementSibling).toHaveTextContent(String(value))
    }
    expect(screen.getByText('Email').compareDocumentPosition(section) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('hides zero metrics and the section when all zero', async () => {
    vi.spyOn(posts, 'getProfileActivity').mockResolvedValue({ data: zero } as never)
    renderProfile()
    await screen.findByRole('heading', { name: 'Ada' })
    await act(async () => {})
    expect(screen.queryByRole('region', { name: 'Profile activity' })).not.toBeInTheDocument()
  })

  it('keeps the profile visible on loading and failure and retries', async () => {
    let reject!: (reason: Error) => void
    vi.spyOn(posts, 'getProfileActivity')
      .mockReturnValueOnce(new Promise((_, fail) => { reject = fail }) as never)
      .mockResolvedValueOnce({ data: { ...zero, postsCreated: 1 } } as never)
    renderProfile()
    expect(await screen.findByRole('heading', { name: 'Ada' })).toBeInTheDocument()
    expect(screen.getByRole('status', { name: 'Loading activity' })).toBeInTheDocument()
    await act(async () => reject(new Error('Unavailable')))
    expect(screen.getByText('Unavailable')).toBeInTheDocument()
    expect(screen.getByText('ada@example.com')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Retry activity' }))
    expect(await screen.findByText('Posts')).toBeInTheDocument()
    expect(screen.queryByText('Comments')).not.toBeInTheDocument()
  })

  it('discards stale responses when navigating between users', async () => {
    let resolve!: (value: unknown) => void
    vi.spyOn(posts, 'getProfileActivity')
      .mockReturnValueOnce(new Promise((done) => { resolve = done }) as never)
      .mockResolvedValueOnce({ data: { ...zero, commentsAuthored: 2 } } as never)
    renderProfile()
    await screen.findByRole('heading', { name: 'Ada' })
    await userEvent.click(screen.getByRole('link', { name: 'Next user' }))
    await screen.findByRole('heading', { name: 'Grace' })
    await screen.findByText('Comments')
    await act(async () => resolve({ data: counts }))
    expect(screen.queryByText('Posts')).not.toBeInTheDocument()
    expect(screen.getByText('Comments').nextElementSibling).toHaveTextContent('2')
  })

  it('does not show the previous user activity while the next user loads', async () => {
    vi.spyOn(posts, 'getProfileActivity')
      .mockResolvedValueOnce({ data: counts } as never)
      .mockReturnValueOnce(new Promise(() => {}) as never)
    renderProfile()
    await screen.findByText('Posts')
    await userEvent.click(screen.getByRole('link', { name: 'Next user' }))
    await screen.findByRole('heading', { name: 'Grace' })
    expect(screen.getByRole('status', { name: 'Loading activity' })).toBeInTheDocument()
    expect(screen.queryByText('Posts')).not.toBeInTheDocument()
  })

  it.each([null, {}, { ...counts, postsCreated: -1 }, { ...counts, postLikesGiven: '2' }, { ...counts, commentsAuthored: 1.5 }])(
    'rejects malformed activity response %s', async (data) => {
      vi.spyOn(apiClient, 'get').mockResolvedValue({ data } as never)
      await expect(posts.getProfileActivity(1)).rejects.toThrow('Invalid activity response.')
    },
  )

  it('requests the activity endpoint and accepts valid counts', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: counts } as never)
    expect((await posts.getProfileActivity(1)).data).toEqual(counts)
    expect(get).toHaveBeenCalledWith('/api/posts/users/1/activity')
  })
})
