import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../../api/auth'
import * as usersApi from '../../api/users'
import type { UserResponse } from '../../api/types'
import { AuthProvider } from '../../auth/AuthContext'
import { clearRefreshToken, getRefreshToken, setRefreshToken } from '../../auth/authState'
import { resetSharedRefresh } from '../../auth/refreshManager'
import { getAccessToken, setAccessToken } from '../../api/client'
import { AccountSettingsPage } from './AccountSettingsPage'

const userProfile: UserResponse = {
  id: 21,
  username: 'Ada',
  email: 'ada@example.com',
  role: 'USER',
  registeredAt: '2024-01-02T10:00:00',
  lastLoginAt: '2025-03-04T11:30:00',
  updatedAt: '2025-03-05T12:00:00',
}

function jwt(payload: Record<string, unknown>) {
  const encode = (value: object) => btoa(JSON.stringify(value))
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
  return `${encode({ alg: 'none' })}.${encode(payload)}.signature`
}

function renderAccount() {
  setRefreshToken('refresh-token')
  vi.spyOn(authApi, 'refresh').mockResolvedValue({
    data: {
      accessToken: jwt({ sub: '21', role: 'USER' }),
      refreshToken: 'rotated-refresh-token',
      tokenType: 'Bearer',
      expiresIn: 900,
    },
  } as never)

  return render(
    <MemoryRouter initialEntries={['/account']}>
      <AuthProvider>
        <Routes>
          <Route path="/account" element={<AccountSettingsPage />} />
          <Route path="/login" element={<h1>Login page</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.restoreAllMocks()
  sessionStorage.clear()
  setAccessToken(null)
  clearRefreshToken()
  resetSharedRefresh()
})

describe('Phase 10 account settings', () => {
  it('loads the profile by JWT subject without requesting /api/users/me', async () => {
    const getUser = vi.spyOn(usersApi, 'getUser').mockResolvedValue({ data: userProfile } as never)
    renderAccount()

    expect(await screen.findByDisplayValue('Ada')).toBeInTheDocument()
    expect(screen.getByDisplayValue('ada@example.com')).toBeInTheDocument()
    expect(getUser).toHaveBeenCalledExactlyOnceWith(21)
  })

  it('validates profile fields and saves updated profile values', async () => {
    const user = userEvent.setup()
    vi.spyOn(usersApi, 'getUser').mockResolvedValue({ data: userProfile } as never)
    const updateProfile = vi.spyOn(usersApi, 'updateMyProfile').mockResolvedValue({
      data: { ...userProfile, username: 'Grace', email: 'grace@example.com' },
    } as never)
    renderAccount()
    await screen.findByDisplayValue('Ada')

    const email = screen.getByLabelText('Email')
    await user.clear(email)
    await user.type(email, 'not-an-email')
    await user.click(screen.getByRole('button', { name: 'Save profile' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Enter a valid email address, for example name@example.com.',
    )
    expect(email).toHaveAccessibleDescription('Enter a valid email address, for example name@example.com.')
    expect(updateProfile).not.toHaveBeenCalled()

    await user.clear(email)
    await user.click(screen.getByRole('button', { name: 'Save profile' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Email is required.')
    expect(email).toHaveAccessibleDescription('Enter a valid email address, for example name@example.com.')
    expect(updateProfile).not.toHaveBeenCalled()

    fireEvent.change(email, { target: { value: 'x'.repeat(255) } })
    await user.click(screen.getByRole('button', { name: 'Save profile' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Email must not exceed 254 characters.')
    expect(email).toHaveAttribute('maxlength', '254')
    expect(updateProfile).not.toHaveBeenCalled()

    await user.clear(screen.getByLabelText('Username'))
    await user.type(screen.getByLabelText('Username'), 'Grace')
    fireEvent.change(email, { target: { value: 'grace@example.com' } })
    await user.click(screen.getByRole('button', { name: 'Save profile' }))

    expect(await screen.findByText('Profile updated successfully.')).toBeInTheDocument()
    expect(updateProfile).toHaveBeenCalledExactlyOnceWith({
      username: 'Grace',
      email: 'grace@example.com',
    })
  })

  it('keeps required and overlength email errors distinct', async () => {
    vi.spyOn(usersApi, 'getUser').mockResolvedValue({ data: userProfile } as never)
    const updateProfile = vi.spyOn(usersApi, 'updateMyProfile')
    renderAccount()
    await screen.findByDisplayValue('Ada')

    const email = screen.getByLabelText('Email')
    await userEvent.clear(email)
    await userEvent.click(screen.getByRole('button', { name: 'Save profile' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Email is required.')
    expect(email).toHaveAccessibleDescription('Enter a valid email address, for example name@example.com.')

    fireEvent.change(email, { target: { value: 'x'.repeat(255) } })
    await userEvent.click(screen.getByRole('button', { name: 'Save profile' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Email must not exceed 254 characters.')
    expect(email).toHaveAccessibleDescription('Enter a valid email address, for example name@example.com.')
    expect(updateProfile).not.toHaveBeenCalled()
  })

  it('keeps server-side profile errors distinct from email guidance', async () => {
    vi.spyOn(usersApi, 'getUser').mockResolvedValue({ data: userProfile } as never)
    vi.spyOn(usersApi, 'updateMyProfile').mockRejectedValue({
      response: { status: 409, data: { message: 'Email is already in use.' } },
      isAxiosError: true,
    })
    renderAccount()
    await screen.findByDisplayValue('Ada')
    await userEvent.click(screen.getByRole('button', { name: 'Save profile' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Email is already in use.')
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
      'Enter a valid email address, for example name@example.com.',
    )
  })

  it('validates password inputs and accepts a successful password response', async () => {
    const user = userEvent.setup()
    vi.spyOn(usersApi, 'getUser').mockResolvedValue({ data: userProfile } as never)
    const changePassword = vi.spyOn(usersApi, 'changeMyPassword').mockResolvedValue({ status: 200 } as never)
    renderAccount()
    await screen.findByDisplayValue('Ada')

    await user.type(screen.getByLabelText('Current password'), 'same-password')
    await user.type(screen.getByLabelText('New password'), 'same-password')
    await user.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await screen.findByText('The new password must be different from the current password.')).toBeInTheDocument()
    expect(changePassword).not.toHaveBeenCalled()

    await user.clear(screen.getByLabelText('New password'))
    await user.type(screen.getByLabelText('New password'), 'new-password')
    await user.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await screen.findByText('Password changed successfully.')).toBeInTheDocument()
    expect(changePassword).toHaveBeenCalledExactlyOnceWith({
      currentPassword: 'same-password',
      newPassword: 'new-password',
    })
    expect(screen.getByLabelText('Current password')).toHaveValue('')
    expect(screen.getByLabelText('New password')).toHaveValue('')
  })

  it('requires explicit deletion confirmation and clears authentication after 204', async () => {
    const user = userEvent.setup()
    vi.spyOn(usersApi, 'getUser').mockResolvedValue({ data: userProfile } as never)
    const deleteAccount = vi.spyOn(usersApi, 'deleteMyAccount').mockResolvedValue({ status: 204 } as never)
    const logout = vi.spyOn(authApi, 'logout').mockResolvedValue({ status: 200 } as never)
    renderAccount()
    await screen.findByDisplayValue('Ada')

    await user.click(screen.getByRole('button', { name: 'Delete account' }))
    const confirmButton = screen.getByRole('button', { name: 'Permanently delete' })
    expect(confirmButton).toBeDisabled()
    await user.type(screen.getByLabelText('Type DELETE to confirm'), 'DELETE')
    expect(confirmButton).toBeEnabled()
    await user.click(confirmButton)

    expect(await screen.findByRole('heading', { name: 'Login page' })).toBeInTheDocument()
    expect(deleteAccount).toHaveBeenCalledExactlyOnceWith()
    expect(logout).toHaveBeenCalledExactlyOnceWith({ refreshToken: 'rotated-refresh-token' })
    await waitFor(() => {
      expect(getRefreshToken()).toBeNull()
      expect(sessionStorage.getItem('leaderboard.refreshToken')).toBeNull()
      expect(getAccessToken()).toBeNull()
    })
  })

  it('shows profile loading errors and retries', async () => {
    const getUser = vi.spyOn(usersApi, 'getUser')
      .mockRejectedValueOnce(new Error('Profile unavailable'))
      .mockResolvedValueOnce({ data: userProfile } as never)
    renderAccount()

    expect(await screen.findByText('Profile unavailable')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByDisplayValue('Ada')).toBeInTheDocument()
    expect(getUser).toHaveBeenCalledTimes(2)
  })
})
