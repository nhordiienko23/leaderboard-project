import axios from 'axios'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as usersApi from '../../api/users'
import type { PageResponse, UserResponse } from '../../api/types'
import { UserDirectoryPage } from './UserDirectoryPage'
import { UserProfilePage } from './UserProfilePage'

const firstUser: UserResponse = {
  id: 21,
  username: 'Ada',
  email: 'ada@example.com',
  role: 'ADMIN',
  registeredAt: '2024-01-02T10:00:00',
  lastLoginAt: '2025-03-04T11:30:00',
  updatedAt: '2025-03-05T12:00:00',
}

const secondUser: UserResponse = {
  ...firstUser,
  id: 22,
  username: 'Grace',
  role: 'USER',
  lastLoginAt: null,
}

function pageResponse(
  content: UserResponse[],
  overrides: Partial<PageResponse<UserResponse>> = {},
): PageResponse<UserResponse> {
  return {
    content,
    number: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    first: true,
    last: true,
    ...overrides,
  }
}

function renderDirectory() {
  return render(<MemoryRouter><UserDirectoryPage /></MemoryRouter>)
}

function renderProfile(id = '21') {
  return render(
    <MemoryRouter initialEntries={[`/users/${id}`]}>
      <Routes>
        <Route path="/users/:id" element={<UserProfilePage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.restoreAllMocks()
})

describe('Phase 9 user directory and profiles', () => {
  it('uses the PageResponse fields, displays users and links to profiles', async () => {
    const getUsers = vi.spyOn(usersApi, 'getUsers').mockResolvedValue({
      data: pageResponse([firstUser, secondUser], { totalElements: 41, totalPages: 3, last: false }),
    } as never)
    renderDirectory()

    const adaLink = await screen.findByRole('link', { name: 'View profile for Ada' })
    expect(adaLink).toHaveAttribute('href', '/users/21')
    expect(screen.getByText('ADMIN')).toBeInTheDocument()
    expect(screen.getByText('Page 1 of 3 · 41 members')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeEnabled()
    expect(getUsers).toHaveBeenCalledWith({ page: 0, size: 20 })
  })

  it('requests the next page and respects the returned page/last fields', async () => {
    const getUsers = vi.spyOn(usersApi, 'getUsers')
      .mockResolvedValueOnce({
        data: pageResponse([firstUser], { totalElements: 21, totalPages: 2, last: false }),
      } as never)
      .mockResolvedValueOnce({
        data: pageResponse([secondUser], {
          number: 1,
          first: false,
          last: true,
          totalElements: 21,
          totalPages: 2,
        }),
      } as never)
    const user = userEvent.setup()
    renderDirectory()

    await screen.findByRole('link', { name: 'View profile for Ada' })
    await user.click(screen.getByRole('button', { name: 'Next page' }))

    expect(await screen.findByRole('link', { name: 'View profile for Grace' })).toHaveAttribute('href', '/users/22')
    expect(getUsers).toHaveBeenNthCalledWith(2, { page: 1, size: 20 })
    expect(screen.getByText('Page 2 of 2 · 21 members')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
  })

  it('shows loading and empty states', async () => {
    vi.spyOn(usersApi, 'getUsers').mockReturnValueOnce(new Promise(() => {}) as never)
    const { unmount } = renderDirectory()
    expect(screen.getByRole('status', { name: 'Loading users' })).toBeInTheDocument()
    unmount()

    vi.spyOn(usersApi, 'getUsers').mockResolvedValueOnce({ data: pageResponse([]) } as never)
    renderDirectory()
    expect(await screen.findByRole('heading', { name: 'No users found' })).toBeInTheDocument()
  })

  it('shows forbidden and retryable network states', async () => {
    vi.spyOn(usersApi, 'getUsers').mockRejectedValueOnce({
      response: { status: 403, data: { message: 'Forbidden' } },
      isAxiosError: true,
    })
    renderDirectory()
    expect(await screen.findByRole('heading', { name: 'Permission denied' })).toBeInTheDocument()

    const getUsers = vi.spyOn(usersApi, 'getUsers')
      .mockRejectedValueOnce(new axios.AxiosError('Network Error', 'ERR_NETWORK'))
      .mockResolvedValueOnce({ data: pageResponse([firstUser]) } as never)
    const user = userEvent.setup()
    renderDirectory()
    expect(await screen.findByText('Network error. Check your connection and try again.')).toBeInTheDocument()
    const callsBeforeRetry = getUsers.mock.calls.length
    await user.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByRole('link', { name: 'View profile for Ada' })).toBeInTheDocument()
    await waitFor(() => expect(getUsers).toHaveBeenCalledTimes(callsBeforeRetry + 1))
  })

  it('retrieves and displays only supported profile details', async () => {
    const getUser = vi.spyOn(usersApi, 'getUser').mockResolvedValue({ data: firstUser } as never)
    renderProfile()

    expect(await screen.findByRole('heading', { name: 'Ada' })).toBeInTheDocument()
    expect(screen.getByText('ada@example.com')).toBeInTheDocument()
    expect(screen.getByText('ADMIN')).toBeInTheDocument()
    expect(screen.getByText('Email')).toBeInTheDocument()
    expect(screen.queryByText(/posts|likes/i)).not.toBeInTheDocument()
    expect(getUser).toHaveBeenCalledWith(21)
  })

  it('formats profile dates with English abbreviated months and preserves date values', async () => {
    const datedUser: UserResponse = {
      ...firstUser,
      registeredAt: '2026-11-23T10:00:00',
      lastLoginAt: '2026-02-04T11:30:00',
      updatedAt: '2026-07-09T12:00:00',
    }
    vi.spyOn(usersApi, 'getUser').mockResolvedValue({ data: datedUser } as never)
    renderProfile()

    await screen.findByRole('heading', { name: 'Ada' })
    expect(screen.getByText('Nov 23, 2026')).toHaveAttribute('datetime', datedUser.registeredAt)
    expect(screen.getByText('Feb 4, 2026')).toHaveAttribute('datetime', datedUser.lastLoginAt)
    expect(screen.getByText('Jul 9, 2026')).toHaveAttribute('datetime', datedUser.updatedAt)
  })

  it('keeps profile dates in English when the browser default locale is French', async () => {
    const originalFormat = Date.prototype.toLocaleDateString
    vi.spyOn(Date.prototype, 'toLocaleDateString').mockImplementation(function (this: Date, locales, options) {
      return originalFormat.call(this, locales ?? 'fr-FR', options)
    })
    const datedUser: UserResponse = {
      ...firstUser,
      registeredAt: '2026-11-23T10:00:00',
      lastLoginAt: '2026-02-04T11:30:00',
      updatedAt: '2026-07-09T12:00:00',
    }
    vi.spyOn(usersApi, 'getUser').mockResolvedValue({ data: datedUser } as never)
    renderProfile()

    await screen.findByRole('heading', { name: 'Ada' })
    expect(screen.getByText('Nov 23, 2026')).toHaveAttribute('datetime', datedUser.registeredAt)
    expect(screen.getByText('Feb 4, 2026')).toHaveAttribute('datetime', datedUser.lastLoginAt)
    expect(screen.getByText('Jul 9, 2026')).toHaveAttribute('datetime', datedUser.updatedAt)
  })

  it('keeps timezone-offset dates interpreted in the local timezone', async () => {
    const registeredAt = '2026-11-23T00:30:00+14:00'
    vi.spyOn(usersApi, 'getUser').mockResolvedValue({
      data: { ...firstUser, registeredAt },
    } as never)
    renderProfile()

    await screen.findByRole('heading', { name: 'Ada' })
    const expectedLocalDate = new Intl.DateTimeFormat('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    }).format(new Date(registeredAt))
    expect(screen.getByText(expectedLocalDate)).toHaveAttribute('datetime', registeredAt)
  })

  it('formats directory dates in English when the browser default locale is French', async () => {
    const originalFormat = Date.prototype.toLocaleDateString
    vi.spyOn(Date.prototype, 'toLocaleDateString').mockImplementation(function (this: Date, locales, options) {
      return originalFormat.call(this, locales ?? 'fr-FR', options)
    })
    vi.spyOn(usersApi, 'getUsers').mockResolvedValue({
      data: pageResponse([{ ...firstUser, registeredAt: '2026-11-23T10:00:00' }]),
    } as never)
    renderDirectory()

    expect(await screen.findByText('Nov 23, 2026')).toHaveAttribute('datetime', '2026-11-23T10:00:00')
  })

  it('preserves local-time interpretation and the datetime value for offset directory dates', async () => {
    const registeredAt = '2026-11-23T00:30:00+14:00'
    vi.spyOn(usersApi, 'getUsers').mockResolvedValue({
      data: pageResponse([{ ...firstUser, registeredAt }]),
    } as never)
    renderDirectory()

    const expectedLocalDate = new Intl.DateTimeFormat('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    }).format(new Date(registeredAt))
    expect(await screen.findByText(expectedLocalDate)).toHaveAttribute('datetime', registeredAt)
  })

  it('handles a missing profile and unavailable last-login date', async () => {
    vi.spyOn(usersApi, 'getUser').mockResolvedValue({ data: secondUser } as never)
    renderProfile('22')
    expect(await screen.findByRole('heading', { name: 'Grace' })).toBeInTheDocument()
    expect(screen.getByText('No login recorded')).toBeInTheDocument()

    vi.spyOn(usersApi, 'getUser').mockRejectedValueOnce({
      response: { status: 404, data: { message: 'User not found' } },
      isAxiosError: true,
    })
    renderProfile('999')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })

  it('rejects invalid profile IDs without making an API request', async () => {
    const getUser = vi.spyOn(usersApi, 'getUser')
    renderProfile('not-an-id')

    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
    expect(getUser).not.toHaveBeenCalled()
  })
})
