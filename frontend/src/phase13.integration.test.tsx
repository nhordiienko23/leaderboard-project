import { AxiosHeaders, type AxiosAdapter } from 'axios'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { apiBaseUrl, apiClient, setAccessToken } from './api/client'
import { AuthProvider } from './auth/AuthContext'
import { clearRefreshToken, REFRESH_TOKEN_STORAGE_KEY } from './auth/authState'
import App from './App'

const post = {
  id: 7,
  content: 'Gateway-backed post',
  userId: 42,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-02T00:00:00Z',
}

const comment = {
  id: 3,
  content: 'Gateway-backed comment',
  userId: 42,
  postId: 7,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-02T00:00:00Z',
}

const springPage = <T,>(content: T[]) => ({
  content,
  page: { totalElements: content.length, totalPages: content.length ? 1 : 0, size: 10, number: 0 },
})

function token(role: 'USER' | 'ADMIN' = 'USER') {
  const payload = btoa(JSON.stringify({ sub: '42', role }))
  return `header.${payload}.signature`
}

function installGatewayAdapter() {
  const comments = [comment]
  let postLiked = false
  const requests: Array<{
    baseURL?: string
    method?: string
    url?: string
    authorization?: unknown
    data?: unknown
  }> = []

  const adapter: AxiosAdapter = async (config) => {
    const request = {
      baseURL: config.baseURL,
      method: config.method?.toUpperCase(),
      url: config.url,
      authorization: config.headers.get('Authorization'),
      data: typeof config.data === 'string' ? JSON.parse(config.data) as unknown : config.data,
    }
    requests.push(request)

    let data: unknown = {}
    let status = 200
    if (request.method === 'POST' && request.url === '/api/auth/refresh') {
      data = {
        accessToken: token(),
        refreshToken: 'rotated-refresh-token',
        tokenType: 'Bearer',
        expiresIn: 900,
      }
    } else if (request.method === 'GET' && request.url === '/api/users/42/summary') {
      data = { id: 42, username: 'test-user' }
    } else if (request.method === 'GET' && request.url === '/api/posts') {
      data = springPage([post])
    } else if (request.method === 'GET' && request.url === '/api/posts/7') {
      data = post
    } else if (request.method === 'GET' && request.url === '/api/posts/7/engagement') {
      data = { likeCount: postLiked ? 1 : 0, commentCount: comments.length, likedByMe: request.authorization ? postLiked : null }
    } else if (request.method === 'GET' && request.url === '/api/posts/7/comments') {
      data = springPage(comments)
    } else if (request.method === 'GET' && request.url?.startsWith('/api/posts/7/comments/') && request.url.endsWith('/engagement')) {
      data = { likeCount: 0, likedByMe: request.authorization ? false : null }
    } else if (request.method === 'POST' && request.url === '/api/posts/7/comments') {
      status = 201
      data = { ...comment, id: 8, content: (request.data as { content: string }).content }
      comments.push(data as typeof comment)
    } else if (request.method === 'POST' && request.url === '/api/posts/7/likes') {
      status = 201
      postLiked = true
      data = { id: 9, userId: 42, postId: 7, commentId: null, createdAt: post.createdAt }
    } else if (request.method === 'GET' && request.url === '/api/leaderboard/top-10') {
      data = [{ userId: 42, username: 'test-user', points: 15 }]
    } else if (request.method === 'GET' && request.url === '/api/leaderboard/top-100') {
      data = [{ userId: 42, username: 'test-user', points: 15 }]
    } else {
      throw new Error(`Unexpected gateway request: ${request.method} ${request.url}`)
    }

    return {
      data,
      status,
      statusText: status === 201 ? 'Created' : 'OK',
      headers: new AxiosHeaders(),
      config,
    }
  }

  apiClient.defaults.adapter = adapter
  return requests
}

function renderApp(initialEntry: string) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  sessionStorage.clear()
  clearRefreshToken()
  setAccessToken(null)
  apiClient.defaults.adapter = undefined
})

describe('Phase 13 frontend integration', () => {
  it('loads the public post feed, follows a post to comments, and switches leaderboard ranges through the gateway client', async () => {
    const requests = installGatewayAdapter()
    const user = userEvent.setup()
    renderApp('/posts')

    await user.click(await screen.findByRole('link', { name: post.content }))
    expect(await screen.findByText(comment.content)).toBeInTheDocument()
    await waitFor(() => expect(screen.getAllByText('test-user')).toHaveLength(2))
    expect(screen.queryByText('User #42')).not.toBeInTheDocument()

    cleanup()
    renderApp('/leaderboard')
    expect(await screen.findByText('test-user')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Top 100' }))
    await waitFor(() => {
      expect(requests.some((request) => request.url === '/api/leaderboard/top-100')).toBe(true)
    })

    expect(requests.map(({ url }) => url)).toEqual(expect.arrayContaining([
      '/api/posts',
      '/api/posts/7',
      '/api/posts/7/engagement',
      '/api/posts/7/comments',
      '/api/posts/7/comments/3/engagement',
      '/api/users/42/summary',
      '/api/leaderboard/top-10',
      '/api/leaderboard/top-100',
    ]))
    expect(requests.every((request) => request.baseURL === apiBaseUrl)).toBe(true)
    expect(requests.every((request) => request.url?.startsWith('/api/'))).toBe(true)
  })

  it('restores a session, rotates the refresh token, and creates a comment and like from the post detail flow', async () => {
    const requests = installGatewayAdapter()
    sessionStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, 'initial-refresh-token')
    const user = userEvent.setup()
    renderApp('/posts/7')

    const commentInput = await screen.findByLabelText('Create a comment')
    await user.type(commentInput, 'A verified comment')
    await user.click(screen.getByRole('button', { name: 'Comment' }))
    expect(await screen.findByText('A verified comment')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Like post 7' }))
    expect(await screen.findByText('Like confirmed.')).toBeInTheDocument()

    await waitFor(() => {
      expect(sessionStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)).toBe('rotated-refresh-token')
    })
    expect(requests).toEqual(expect.arrayContaining([
      expect.objectContaining({
        method: 'POST',
        url: '/api/auth/refresh',
        baseURL: apiBaseUrl,
      }),
      expect.objectContaining({
        method: 'POST',
        url: '/api/posts/7/comments',
        data: { content: 'A verified comment' },
        authorization: 'Bearer ' + token(),
        baseURL: apiBaseUrl,
      }),
      expect.objectContaining({
        method: 'POST',
        url: '/api/posts/7/likes',
        authorization: 'Bearer ' + token(),
        baseURL: apiBaseUrl,
      }),
      expect.objectContaining({
        method: 'GET',
        url: '/api/posts/7/engagement',
        authorization: 'Bearer ' + token(),
        baseURL: apiBaseUrl,
      }),
    ]))
    expect(requests.every((request) => request.baseURL === apiBaseUrl)).toBe(true)
    expect(requests.every((request) => request.url?.startsWith('/api/'))).toBe(true)
  })
})
