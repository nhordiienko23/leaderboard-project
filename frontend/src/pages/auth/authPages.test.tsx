import axios, { AxiosHeaders, type AxiosAdapter } from 'axios'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import * as authApi from '../../api/auth'
import * as postsApi from '../../api/posts'
import { apiClient, getAccessToken, setAccessToken } from '../../api/client'
import { AuthProvider } from '../../auth/AuthContext'
import { clearRefreshToken, getRefreshToken, REFRESH_TOKEN_STORAGE_KEY } from '../../auth/authState'
import { resetSharedRefresh } from '../../auth/refreshManager'

const userResponse = {
  id: 42, username: 'Ada', email: 'ada@example.com', role: 'USER',
  registeredAt: '2026-01-01', lastLoginAt: null, updatedAt: '2026-01-01',
}
const token = `header.${btoa(JSON.stringify({ sub: '42', role: 'USER' }))}.signature`
const tokens = { accessToken: token, refreshToken: 'refresh-token', tokenType: 'Bearer', expiresIn: 900 }

function renderApp(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><AuthProvider><App /></AuthProvider></MemoryRouter>)
}

function httpError(status: number, message: string, fieldErrors?: Record<string, string>) {
  return new axios.AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, {
    data: { message, fieldErrors }, status, statusText: 'Error', headers: {}, config: {} as never,
  })
}

async function submitLogin() {
  fireEvent.change(screen.getByLabelText('Username or email'), { target: { value: 'ada@example.com' } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password' } })
  fireEvent.submit(screen.getByRole('button', { name: 'Login' }).closest('form')!)
}

async function submitRegistration() {
  fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'Ada' } })
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'ada@example.com' } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password' } })
  fireEvent.submit(screen.getByRole('button', { name: 'Register' }).closest('form')!)
}

beforeEach(() => {
  vi.restoreAllMocks()
  sessionStorage.clear()
  setAccessToken(null)
  clearRefreshToken()
  resetSharedRefresh()
  vi.spyOn(postsApi, 'getPosts').mockResolvedValue({ data: {
    content: [], totalElements: 0, totalPages: 0, size: 10, number: 0, first: true, last: true,
  } } as never)
})

describe('login page', () => {
  it('renders labeled fields, autocomplete, register link, and accessible required errors', async () => {
    const login = vi.spyOn(authApi, 'login')
    renderApp('/login')
    const main = within(await screen.findByRole('main'))
    const identifier = main.getByLabelText('Username or email')
    const password = main.getByLabelText('Password')
    expect(identifier).toHaveAttribute('autocomplete', 'username')
    expect(identifier).toHaveAttribute('maxlength', '254')
    expect(password).toHaveAttribute('autocomplete', 'current-password')
    expect(password).toHaveAttribute('maxlength', '72')
    expect(main.getByRole('link', { name: 'Register' })).toHaveAttribute('href', '/register')
    await userEvent.click(main.getByRole('button', { name: 'Login' }))
    expect(identifier).toHaveAccessibleDescription('Username or email is required.')
    expect(password).toHaveAccessibleDescription('Password is required.')
    expect(identifier).toHaveAttribute('aria-invalid', 'true')
    expect(login).not.toHaveBeenCalled()
  })

  it('validates length and UTF-8 byte limits before submitting', async () => {
    const login = vi.spyOn(authApi, 'login')
    renderApp('/login')
    await screen.findByRole('heading', { name: 'Login' })
    fireEvent.change(screen.getByLabelText('Username or email'), { target: { value: 'x'.repeat(255) } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'é'.repeat(37) } })
    fireEvent.submit(screen.getByRole('button', { name: 'Login' }).closest('form')!)
    expect(screen.getByLabelText('Username or email')).toHaveAccessibleDescription('Username or email must not exceed 254 characters.')
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('Password must not exceed 72 UTF-8 bytes.')
    expect(login).not.toHaveBeenCalled()
  })

  it('submits credentials, establishes a session, and redirects to posts', async () => {
    const login = vi.spyOn(authApi, 'login').mockResolvedValue({ status: 200, data: tokens } as never)
    renderApp('/login')
    await screen.findByRole('heading', { name: 'Login' })
    await submitLogin()
    expect(await screen.findByRole('heading', { name: 'Posts' })).toBeInTheDocument()
    expect(login).toHaveBeenCalledExactlyOnceWith({ login: 'ada@example.com', password: 'password' })
    expect(getAccessToken()).toBe(token)
    expect(sessionStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)).toBe('refresh-token')
  })

  it.each([
    ['invalid credentials', httpError(401, 'Invalid username/email or password'), 'Invalid username/email or password'],
    ['backend validation', httpError(400, 'login: Invalid login', { login: 'Invalid login' }), 'login: Invalid login'],
    ['network failure', new axios.AxiosError('Network Error', 'ERR_NETWORK'), 'Network error. Check your connection and try again.'],
  ])('displays %s errors', async (_label, failure, message) => {
    vi.spyOn(authApi, 'login').mockRejectedValue(failure)
    renderApp('/login')
    await screen.findByRole('heading', { name: 'Login' })
    await submitLogin()
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    if (_label === 'backend validation') {
      expect(screen.getByLabelText('Username or email')).toHaveAccessibleDescription('Invalid login')
    }
    expect(getAccessToken()).toBeNull()
  })

  it('prevents duplicate submissions while loading', async () => {
    let resolve!: (value: never) => void
    const login = vi.spyOn(authApi, 'login').mockReturnValue(new Promise((done) => { resolve = done }) as never)
    renderApp('/login')
    await screen.findByRole('heading', { name: 'Login' })
    await submitLogin()
    const button = screen.getByRole('button', { name: 'Login' })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')
    fireEvent.submit(button.closest('form')!)
    expect(login).toHaveBeenCalledTimes(1)
    await act(async () => resolve({ status: 200, data: tokens } as never))
    expect(await screen.findByRole('heading', { name: 'Posts' })).toBeInTheDocument()
  })
})

describe('registration page', () => {
  it('renders fields, autocomplete, login link, and accessible required errors', async () => {
    const register = vi.spyOn(authApi, 'register')
    renderApp('/register')
    const main = within(await screen.findByRole('main'))
    const username = main.getByLabelText('Username')
    const email = main.getByLabelText('Email')
    const password = main.getByLabelText('Password')
    expect(username).toHaveAttribute('autocomplete', 'username')
    expect(email).toHaveAttribute('autocomplete', 'email')
    expect(password).toHaveAttribute('autocomplete', 'new-password')
    expect(username).toHaveAttribute('maxlength', '50')
    expect(email).toHaveAttribute('maxlength', '254')
    expect(password).toHaveAttribute('maxlength', '72')
    expect(main.getByRole('link', { name: 'Login' })).toHaveAttribute('href', '/login')
    await userEvent.click(main.getByRole('button', { name: 'Register' }))
    expect(username).toHaveAccessibleDescription('Username is required.')
    expect(email).toHaveAccessibleDescription(
      'Enter a valid email address, for example name@example.com. Email is required.',
    )
    expect(password).toHaveAccessibleDescription('Password is required.')
    expect(register).not.toHaveBeenCalled()
  })

  it('rejects invalid email and values beyond all limits', async () => {
    const register = vi.spyOn(authApi, 'register')
    renderApp('/register')
    await screen.findByRole('heading', { name: 'Register' })
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'x'.repeat(51) } })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'x'.repeat(243) + '@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'x'.repeat(73) } })
    const form = screen.getByRole('button', { name: 'Register' }).closest('form')!
    fireEvent.submit(form)
    expect(screen.getByLabelText('Username')).toHaveAccessibleDescription('Username must not exceed 50 characters.')
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
      'Enter a valid email address, for example name@example.com. Email must not exceed 254 characters.',
    )
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('Password must not exceed 72 characters.')
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'not-an-email' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'é'.repeat(37) } })
    fireEvent.submit(form)
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
      'Enter a valid email address, for example name@example.com.',
    )
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('Password must not exceed 72 UTF-8 bytes.')
    expect(register).not.toHaveBeenCalled()
  })

  it('accepts boundary lengths, redirects only on 201, and does not authenticate', async () => {
    const register = vi.spyOn(authApi, 'register').mockResolvedValue({ status: 201, data: userResponse } as never)
    const login = vi.spyOn(authApi, 'login')
    renderApp('/register')
    await screen.findByRole('heading', { name: 'Register' })
    const username = 'a'.repeat(50)
    const email = 'a'.repeat(242) + '@example.com'
    const password = 'p'.repeat(72)
    expect(email).toHaveLength(254)
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: username } })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: email } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } })
    fireEvent.submit(screen.getByRole('button', { name: 'Register' }).closest('form')!)
    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
    expect(register).toHaveBeenCalledExactlyOnceWith({ username, email, password })
    expect(login).not.toHaveBeenCalled()
    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
    expect(sessionStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)).toBeNull()
  })

  it('does not redirect for an unexpected successful status', async () => {
    vi.spyOn(authApi, 'register').mockResolvedValue({ status: 200, data: userResponse } as never)
    renderApp('/register')
    await screen.findByRole('heading', { name: 'Register' })
    await submitRegistration()
    expect(await screen.findByRole('alert')).toHaveTextContent('The authentication service did not confirm registration.')
    expect(screen.getByRole('heading', { name: 'Register' })).toBeInTheDocument()
    expect(getAccessToken()).toBeNull()
  })

  it.each([
    ['backend validation', httpError(400, 'email: Invalid email', { email: 'Email is already in use.' }), 'email: Invalid email'],
    ['network failure', new axios.AxiosError('Network Error', 'ERR_NETWORK'), 'Network error. Check your connection and try again.'],
  ])('displays %s errors', async (_label, failure, message) => {
    vi.spyOn(authApi, 'register').mockRejectedValue(failure)
    renderApp('/register')
    await screen.findByRole('heading', { name: 'Register' })
    await submitRegistration()
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    if (_label === 'backend validation') {
      expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
        'Enter a valid email address, for example name@example.com. Email is already in use.',
      )
    }
    expect(getAccessToken()).toBeNull()
  })

  it('keeps email guidance visible alongside a generic registration server error', async () => {
    vi.spyOn(authApi, 'register').mockRejectedValue(httpError(500, 'Registration service unavailable'))
    renderApp('/register')
    await screen.findByRole('heading', { name: 'Register' })
    await submitRegistration()

    expect(await screen.findByRole('alert')).toHaveTextContent('Registration service unavailable')
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription(
      'Enter a valid email address, for example name@example.com.',
    )
  })

  it('prevents duplicate submissions while loading', async () => {
    let resolve!: (value: never) => void
    const register = vi.spyOn(authApi, 'register').mockReturnValue(new Promise((done) => { resolve = done }) as never)
    renderApp('/register')
    await screen.findByRole('heading', { name: 'Register' })
    await submitRegistration()
    const button = screen.getByRole('button', { name: 'Register' })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')
    fireEvent.submit(button.closest('form')!)
    expect(register).toHaveBeenCalledTimes(1)
    await act(async () => resolve({ status: 201, data: userResponse } as never))
    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
  })
})

describe('guest routes', () => {
  it.each(['/login', '/register'])('redirects authenticated users away from %s', async (path) => {
    sessionStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, 'old-refresh')
    vi.spyOn(authApi, 'refresh').mockResolvedValue({ data: tokens } as never)
    renderApp(path)
    expect(await screen.findByRole('heading', { name: 'Posts' })).toBeInTheDocument()
    await waitFor(() => expect(sessionStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)).toBe('refresh-token'))
  })
})

it('sends registration and login through the existing API client', async () => {
  const requests: { method?: string; url?: string; data: unknown }[] = []
  const adapter: AxiosAdapter = async (config) => {
    requests.push({
      method: config.method,
      url: config.url,
      data: typeof config.data === 'string' ? JSON.parse(config.data) : config.data,
    })
    const isRegister = config.url === '/api/auth/register'
    const isSummary = config.url === '/api/users/42/summary'
    if (!isRegister && !isSummary && config.url !== '/api/auth/login') throw new Error('Unexpected request')
    return {
      status: isRegister ? 201 : 200,
      statusText: isRegister ? 'Created' : 'OK',
      data: isSummary ? { id: 42, username: 'Ada' } : isRegister ? userResponse : tokens,
      headers: new AxiosHeaders(),
      config,
    }
  }
  const previousAdapter = apiClient.defaults.adapter
  apiClient.defaults.adapter = adapter
  try {
    renderApp('/register')
    await screen.findByRole('heading', { name: 'Register' })
    await submitRegistration()
    await screen.findByRole('heading', { name: 'Login' })
    expect(getAccessToken()).toBeNull()
    await submitLogin()
    expect(await screen.findByRole('heading', { name: 'Posts' })).toBeInTheDocument()
    expect(await screen.findByText('Ada')).toBeInTheDocument()
    expect(requests).toEqual([
      { method: 'post', url: '/api/auth/register', data: { username: 'Ada', email: 'ada@example.com', password: 'password' } },
      { method: 'post', url: '/api/auth/login', data: { login: 'ada@example.com', password: 'password' } },
      { method: 'get', url: '/api/users/42/summary', data: undefined },
    ])
  } finally {
    apiClient.defaults.adapter = previousAdapter
  }
})
