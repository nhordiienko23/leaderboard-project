import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../../auth/AuthContext'
import { clearRefreshToken, REFRESH_TOKEN_STORAGE_KEY } from '../../auth/authState'
import * as auth from '../../api/auth'
import * as posts from '../../api/posts'
import * as comments from '../../api/comments'
import { PostsPage } from './PostsPage'
import { PostDetailPage } from './PostDetailPage'

const text = `${'x'.repeat(100)}\n  https://example.com/${'y'.repeat(100)}  \nend`
const post = { id: 7, userId: 42, content: text, createdAt: '2026-01-01', updatedAt: '2026-01-01' }
const comment = { ...post, id: 3, postId: 7 }
const page = { number: 0, size: 5, totalElements: 1, totalPages: 1, first: true, last: true }

beforeEach(() => {
  vi.restoreAllMocks()
  clearRefreshToken()
  vi.spyOn(posts, 'getPosts').mockResolvedValue({ data: { ...page, content: [post] } } as never)
  vi.spyOn(posts, 'getPost').mockResolvedValue({ data: post } as never)
  vi.spyOn(posts, 'getPostEngagement').mockResolvedValue({ data: { likeCount: 0, commentCount: 1, likedByMe: false } } as never)
  vi.spyOn(comments, 'getComments').mockResolvedValue({ data: { ...page, content: [comment] } } as never)
  vi.spyOn(comments, 'getCommentEngagement').mockResolvedValue({ data: { likeCount: 0, likedByMe: false } } as never)
})

function renderPage(detail: boolean) {
  return render(<MemoryRouter initialEntries={[detail ? '/posts/7' : '/posts']}>
    <AuthProvider><Routes><Route path="/posts" element={<PostsPage />} /><Route path="/posts/:postId" element={<PostDetailPage />} /></Routes></AuthProvider>
  </MemoryRouter>)
}

describe('responsive content markup', () => {
  it.each([false, true])('preserves long post content and wrapping on detail=%s', async (detail) => {
    renderPage(detail)
    const link = await screen.findByRole('link', { name: /x{20}/ })
    expect(link.textContent).toBe(text)
    expect(link).toHaveClass('whitespace-break-spaces', 'wrap-anywhere')
    expect(link.closest('article')).toHaveClass('min-w-0')
    expect(screen.queryByRole('button', { name: 'Edit post 7' })).not.toBeInTheDocument()
  })

  it('preserves comment content and owner controls in detail', async () => {
    sessionStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, 'refresh')
    const token = `header.${btoa(JSON.stringify({ sub: '42', role: 'USER' }))}.signature`
    vi.spyOn(auth, 'refresh').mockResolvedValue({ data: { accessToken: token, refreshToken: 'rotated', tokenType: 'Bearer', expiresIn: 900 } } as never)
    renderPage(true)
    const edit = await screen.findByRole('button', { name: 'Edit comment 3' })
    const commentArticle = edit.closest('article')!
    const content = commentArticle.querySelector('p.whitespace-break-spaces')!
    expect(content.textContent).toBe(text)
    expect(content).toHaveClass('wrap-anywhere')
    expect(screen.getByRole('button', { name: 'Delete comment 3' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Edit post 7' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Delete post 7' })).toBeEnabled()
    await userEvent.click(edit)
    expect(screen.getByLabelText('Comment content')).toHaveValue(text)
  })
})
