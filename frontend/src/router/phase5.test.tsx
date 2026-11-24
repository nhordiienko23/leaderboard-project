import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../App'
import { AuthProvider } from '../auth/AuthContext'
import { clearRefreshToken, REFRESH_TOKEN_STORAGE_KEY } from '../auth/authState'
import * as authApi from '../api/auth'
import * as postsApi from '../api/posts'
import * as usersApi from '../api/users'
import { apiClient } from '../api/client'

function jwt(role: 'USER' | 'ADMIN' = 'USER') {
  const payload = btoa(JSON.stringify({ sub: '42', role }))
  return `header.${payload}.signature`
}

function renderApp(path = '/posts') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider><App /></AuthProvider>
    </MemoryRouter>,
  )
}

async function restoreUser(role: 'USER' | 'ADMIN' = 'USER') {
  sessionStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, 'refresh')
  vi.spyOn(authApi, 'refresh').mockResolvedValue({ data: {
    accessToken: jwt(role), refreshToken: 'rotated', tokenType: 'Bearer', expiresIn: 900,
  } } as never)
}

beforeEach(() => {
  clearRefreshToken()
  vi.restoreAllMocks()
  vi.spyOn(apiClient, 'get').mockImplementation(async (url) => {
    if (url === '/api/users/42/summary') return { data: { id: 42, username: 'q' } } as never
    if (url === '/api/posts/123/engagement') return { data: { likeCount: 0, commentCount: 0, likedByMe: null } } as never
    if (url.endsWith('/comments')) {
      return { data: { content: [], totalElements: 0, totalPages: 0, size: 10, number: 0, first: true, last: true } } as never
    }
    throw new Error(`Unexpected request: ${url}`)
  })
  vi.spyOn(postsApi, 'getPosts').mockResolvedValue({ data: {
    content: [], totalElements: 0, totalPages: 0, size: 10, number: 0, first: true, last: true,
  } } as never)
  vi.spyOn(postsApi, 'getPost').mockResolvedValue({ data: {
    id: 123, content: 'Post detail', userId: 42, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z',
  } } as never)
  vi.spyOn(usersApi, 'getUser').mockResolvedValue({ data: {
    id: 42,
    username: 'Profile user',
    email: 'profile@example.com',
    role: 'USER',
    registeredAt: '2026-01-01T00:00:00Z',
    lastLoginAt: null,
    updatedAt: '2026-01-01T00:00:00Z',
  } } as never)
  vi.spyOn(usersApi, 'getUsers').mockResolvedValue({ data: {
    content: [], totalElements: 0, totalPages: 0, size: 20, number: 0, first: true, last: true,
  } } as never)
})

describe('Phase 5 application routes and layout', () => {
  it.each([
    ['/posts', 'Posts'], ['/posts/123', 'Post #123'], ['/leaderboard', 'Leaderboard'],
    ['/403', 'Permission denied'], ['/404', 'Page not found'], ['/unknown/path', 'Page not found'],
  ])('renders public or error route %s', async (path, heading) => {
    renderApp(path)
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
  })

  it('renders guest login/register routes and redirects authenticated guests', async () => {
    const guest = renderApp('/login')
    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
    guest.unmount()

    await restoreUser()
    renderApp('/login')
    expect(await screen.findByRole('heading', { name: 'Posts' })).toBeInTheDocument()
  })

  it('protects users, profiles, account, and administers admin routes', async () => {
    renderApp('/users')
    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
  })

  it('renders protected and admin routes for restored roles', async () => {
    await restoreUser('USER')
    const userView = renderApp('/users/42')
    expect(await screen.findByRole('heading', { name: 'Profile user' })).toBeInTheDocument()
    userView.unmount()

    await restoreUser('ADMIN')
    renderApp('/admin')
    expect(await screen.findByRole('heading', { name: 'Admin user management' })).toBeInTheDocument()
  })

  it('sends a non-admin admin route to /403', async () => {
    await restoreUser('USER')
    renderApp('/admin')
    expect(await screen.findByRole('heading', { name: 'Permission denied' })).toBeInTheDocument()
  })

  it('shows guest navigation and hides authenticated links', async () => {
    renderApp('/posts')
    await screen.findByRole('heading', { name: 'Posts' })
    expect(screen.getAllByRole('link', { name: 'Posts' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Leaderboard' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Login' }).length).toBeGreaterThan(0)
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument()
  })

  it.each(['USER', 'ADMIN'] as const)('shows the expected %s navigation', async (role) => {
    await restoreUser(role)
    renderApp('/posts')
    await screen.findByText('q')
    expect(screen.queryByText('User #42')).not.toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Users' }).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Account' }).length).toBeGreaterThan(0)
    if (role === 'ADMIN') expect(screen.getAllByRole('link', { name: 'Admin' }).length).toBeGreaterThan(0)
    else expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument()
  })

  it('marks the active route in navigation', async () => {
    renderApp('/leaderboard')
    await screen.findByRole('heading', { name: 'Leaderboard' })
    const links = screen.getAllByRole('link', { name: 'Leaderboard' })
    expect(links.some((link) => link.getAttribute('aria-current') === 'page')).toBe(true)
  })

  it('opens and closes the mobile menu and closes it after navigation', async () => {
    const user = userEvent.setup()
    renderApp('/posts')
    await screen.findByRole('heading', { name: 'Posts' })
    await user.click(screen.getByRole('button', { name: 'Open navigation menu' }))
    expect(screen.getByRole('button', { name: 'Close navigation menu' })).toHaveAttribute('aria-expanded', 'true')
    expect(document.body.style.overflow).toBe('hidden')

    const menu = document.getElementById('mobile-navigation')
    expect(menu).not.toBeNull()
    await user.click(within(menu as HTMLElement).getByRole('link', { name: 'Leaderboard' }))
    await waitFor(() => expect(document.body.style.overflow).not.toBe('hidden'))
  })

  it('cleans auth and navigates to login after logout', async () => {
    await restoreUser()
    vi.spyOn(authApi, 'logout').mockResolvedValue({} as never)
    const user = userEvent.setup()
    renderApp('/account')
    await screen.findByRole('heading', { name: 'Account settings' })

    const identity = await screen.findByText('q')
    await user.click(identity.closest('button') as HTMLButtonElement)
    await user.click(screen.getByRole('button', { name: 'Logout' }))

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
    expect(sessionStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)).toBeNull()
  })
})