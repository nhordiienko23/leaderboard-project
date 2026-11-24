import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import * as adminApi from '../../api/admin'
import * as authApi from '../../api/auth'
import type { PageResponse, UserResponse } from '../../api/types'
import * as usersApi from '../../api/users'
import { AuthProvider } from '../../auth/AuthContext'
import { clearRefreshToken, REFRESH_TOKEN_STORAGE_KEY } from '../../auth/authState'

const userProfile: UserResponse = {
  id: 21,
  username: 'Ada',
  email: 'ada@example.com',
  role: 'USER',
  registeredAt: '2024-01-02T10:00:00',
  lastLoginAt: null,
  updatedAt: '2025-03-05T12:00:00',
}

function jwt(role: 'USER' | 'ADMIN') {
  const payload = btoa(JSON.stringify({ sub: '42', role }))
  return `header.${payload}.signature`
}

function pageResponse(
  content: UserResponse[],
  page = 0,
  totalPages = 1,
): PageResponse<UserResponse> {
  return {
    content,
    number: page,
    size: 20,
    totalElements: totalPages * 20,
    totalPages,
    first: page === 0,
    last: page === totalPages - 1,
  }
}

function renderApp() {
  return render(
    <MemoryRouter initialEntries={['/admin']}>
      <AuthProvider><App /></AuthProvider>
    </MemoryRouter>,
  )
}

async function restoreAdmin(role: 'USER' | 'ADMIN' = 'ADMIN') {
  sessionStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, 'refresh')
  vi.spyOn(authApi, 'refresh').mockResolvedValue({ data: {
    accessToken: jwt(role),
    refreshToken: 'rotated',
    tokenType: 'Bearer',
    expiresIn: 900,
  } } as never)
}

beforeEach(() => {
  clearRefreshToken()
  vi.restoreAllMocks()
})

describe('Phase 11 admin dashboard', () => {
  it('uses the role guard as a convenience boundary for non-admin users', async () => {
    await restoreAdmin('USER')
    const getUsers = vi.spyOn(usersApi, 'getUsers')
    renderApp()

    expect(await screen.findByRole('heading', { name: 'Permission denied' })).toBeInTheDocument()
    expect(getUsers).not.toHaveBeenCalled()
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument()
  })

  it('supports creating, editing, role changes, password updates, and confirmed deletion', async () => {
    await restoreAdmin()
    const user = userEvent.setup()
    vi.spyOn(usersApi, 'getUsers').mockResolvedValue({ data: pageResponse([userProfile]) } as never)
    const createUser = vi.spyOn(adminApi, 'createAdminUser').mockResolvedValue({
      status: 201,
      data: { ...userProfile, id: 22, username: 'Grace', role: 'ADMIN' },
    } as never)
    const updateUser = vi.spyOn(adminApi, 'updateAdminUser').mockResolvedValue({
      data: { ...userProfile, username: 'Augusta' },
    } as never)
    const updateRole = vi.spyOn(adminApi, 'updateAdminUserRole').mockResolvedValue({
      data: { ...userProfile, role: 'ADMIN' },
    } as never)
    const setPassword = vi.spyOn(adminApi, 'setAdminUserPassword').mockResolvedValue({ status: 204 } as never)
    const deleteUser = vi.spyOn(adminApi, 'deleteAdminUser').mockResolvedValue({ status: 204 } as never)

    renderApp()
    expect(await screen.findByRole('heading', { name: 'Admin user management' })).toBeInTheDocument()
    expect(await screen.findByText('ada@example.com')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Create user' }))
    let dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('Username'), 'Grace')
    await user.type(within(dialog).getByLabelText('Email'), 'grace@example.com')
    await user.type(within(dialog).getByLabelText('Password'), 'initial-password')
    await user.selectOptions(within(dialog).getByRole('combobox', { name: 'Role' }), 'ADMIN')
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }))
    expect(await screen.findByText('User created successfully.')).toBeInTheDocument()
    expect(createUser).toHaveBeenCalledExactlyOnceWith({
      username: 'Grace',
      email: 'grace@example.com',
      password: 'initial-password',
      role: 'ADMIN',
    })

    await user.click(screen.getByRole('button', { name: 'Edit Ada' }))
    dialog = screen.getByRole('dialog')
    await user.clear(within(dialog).getByLabelText('Username'))
    await user.type(within(dialog).getByLabelText('Username'), 'Augusta')
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))
    expect(await screen.findByText('User updated successfully.')).toBeInTheDocument()
    expect(updateUser).toHaveBeenCalledExactlyOnceWith(21, {
      username: 'Augusta',
      email: 'ada@example.com',
    })

    await user.click(screen.getByRole('button', { name: 'Change role for Ada' }))
    dialog = screen.getByRole('dialog')
    await user.selectOptions(within(dialog).getByRole('combobox', { name: 'Role' }), 'ADMIN')
    await user.click(within(dialog).getByRole('button', { name: 'Save role' }))
    expect(await screen.findByText('User role updated successfully.')).toBeInTheDocument()
    expect(updateRole).toHaveBeenCalledExactlyOnceWith(21, { role: 'ADMIN' })

    await user.click(screen.getByRole('button', { name: 'Change password for Ada' }))
    dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('New password'), 'new-password')
    await user.click(within(dialog).getByRole('button', { name: 'Set password' }))
    expect(await screen.findByText('Password updated for Ada.')).toBeInTheDocument()
    expect(setPassword).toHaveBeenCalledExactlyOnceWith(21, { newPassword: 'new-password' })

    await user.click(screen.getByRole('button', { name: 'Delete Ada' }))
    dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText(/cannot be undone/)).toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Delete user' }))
    expect(await screen.findByText('Ada was deleted.')).toBeInTheDocument()
    expect(deleteUser).toHaveBeenCalledExactlyOnceWith(21)
  })

  it('loads the next admin-users page using the shared paginated user endpoint', async () => {
    await restoreAdmin()
    const user = userEvent.setup()
    const getUsers = vi.spyOn(usersApi, 'getUsers')
      .mockResolvedValueOnce({ data: pageResponse([userProfile], 0, 2) } as never)
      .mockResolvedValueOnce({ data: pageResponse([{ ...userProfile, id: 22, username: 'Grace' }], 1, 2) } as never)

    renderApp()
    expect(await screen.findByText('Ada')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Next page' }))

    expect(await screen.findByText('Grace')).toBeInTheDocument()
    expect(getUsers).toHaveBeenNthCalledWith(1, { page: 0, size: 20 })
    expect(getUsers).toHaveBeenNthCalledWith(2, { page: 1, size: 20 })
  })

  it('provides accessible email guidance and keeps create/edit validation errors distinct', async () => {
    await restoreAdmin()
    const user = userEvent.setup()
    vi.spyOn(usersApi, 'getUsers').mockResolvedValue({ data: pageResponse([userProfile]) } as never)
    const createUser = vi.spyOn(adminApi, 'createAdminUser')
    const updateUser = vi.spyOn(adminApi, 'updateAdminUser')
      .mockRejectedValue({
        response: { status: 409, data: { message: 'Email is already in use.' } },
        isAxiosError: true,
      })
    renderApp()
    await screen.findByRole('heading', { name: 'Admin user management' })

    await user.click(screen.getByRole('button', { name: 'Create user' }))
    let dialog = screen.getByRole('dialog')
    const createEmail = within(dialog).getByLabelText('Email')
    expect(createEmail).toHaveAccessibleDescription(
      'Enter a valid email address, for example name@example.com.',
    )
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }))
    expect(await within(dialog).findByText('Username is required.')).toBeInTheDocument()
    expect(createUser).not.toHaveBeenCalled()

    await user.type(within(dialog).getByLabelText('Username'), 'Grace')
    await user.type(within(dialog).getByLabelText('Password'), 'initial-password')
    fireEvent.change(createEmail, { target: { value: 'x'.repeat(255) } })
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Email must not exceed 254 characters.')
    expect(createEmail).toHaveAttribute('maxlength', '254')
    expect(createUser).not.toHaveBeenCalled()

    fireEvent.change(createEmail, { target: { value: 'invalid-email' } })
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Enter a valid email address, for example name@example.com.',
    )
    expect(createUser).not.toHaveBeenCalled()

    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    await user.click(screen.getByRole('button', { name: 'Edit Ada' }))
    dialog = screen.getByRole('dialog')
    expect(within(dialog).getByLabelText('Email')).toHaveAccessibleDescription(
      'Enter a valid email address, for example name@example.com.',
    )
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))
    expect(await within(dialog).findByText('Email is already in use.')).toBeInTheDocument()
    expect(within(dialog).getByLabelText('Email')).toHaveAccessibleDescription(
      'Enter a valid email address, for example name@example.com.',
    )
    expect(updateUser).toHaveBeenCalledExactlyOnceWith(21, { username: 'Ada', email: 'ada@example.com' })
  })

  it('keeps required and overlength email errors distinct in create and edit forms', async () => {
    await restoreAdmin()
    const user = userEvent.setup()
    vi.spyOn(usersApi, 'getUsers').mockResolvedValue({ data: pageResponse([userProfile]) } as never)
    const createUser = vi.spyOn(adminApi, 'createAdminUser')
    const updateUser = vi.spyOn(adminApi, 'updateAdminUser')
    renderApp()
    await screen.findByRole('heading', { name: 'Admin user management' })

    await user.click(screen.getByRole('button', { name: 'Create user' }))
    let dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('Username'), 'Grace')
    await user.type(within(dialog).getByLabelText('Password'), 'initial-password')
    const createEmail = within(dialog).getByLabelText('Email')
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Email is required.')
    expect(createEmail).toHaveAccessibleDescription(
      'Enter a valid email address, for example name@example.com.',
    )

    fireEvent.change(createEmail, { target: { value: 'x'.repeat(255) } })
    await user.click(within(dialog).getByRole('button', { name: 'Create user' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Email must not exceed 254 characters.')
    expect(createUser).not.toHaveBeenCalled()

    await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
    await user.click(screen.getByRole('button', { name: 'Edit Ada' }))
    dialog = screen.getByRole('dialog')
    const editEmail = within(dialog).getByLabelText('Email')
    await user.clear(editEmail)
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Email is required.')
    expect(editEmail).toHaveAccessibleDescription(
      'Enter a valid email address, for example name@example.com.',
    )

    fireEvent.change(editEmail, { target: { value: 'x'.repeat(255) } })
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Email must not exceed 254 characters.')
    expect(updateUser).not.toHaveBeenCalled()
  })

  it('moves back to the remaining page after deleting the last user on a page', async () => {
    await restoreAdmin()
    const user = userEvent.setup()
    const getUsers = vi.spyOn(usersApi, 'getUsers')
      .mockResolvedValueOnce({ data: pageResponse([{ ...userProfile, id: 20, username: 'Initial' }], 0, 2) } as never)
      .mockResolvedValueOnce({ data: pageResponse([userProfile], 1, 2) } as never)
      .mockResolvedValueOnce({ data: pageResponse([], 1, 1) } as never)
      .mockResolvedValueOnce({ data: pageResponse([{ ...userProfile, id: 20, username: 'Grace' }]) } as never)
    vi.spyOn(adminApi, 'deleteAdminUser').mockResolvedValue({ status: 204 } as never)

    renderApp()
    expect(await screen.findByText('Initial')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    expect(await screen.findByText('Ada')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Delete Ada' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete user' }))

    expect(await screen.findByText('Grace')).toBeInTheDocument()
    expect(getUsers).toHaveBeenNthCalledWith(1, { page: 0, size: 20 })
    expect(getUsers).toHaveBeenNthCalledWith(2, { page: 1, size: 20 })
    expect(getUsers).toHaveBeenNthCalledWith(3, { page: 1, size: 20 })
    expect(getUsers).toHaveBeenNthCalledWith(4, { page: 0, size: 20 })
  })
})
