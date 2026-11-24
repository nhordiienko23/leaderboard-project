import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import App from './App'
import { AuthProvider } from './auth/AuthContext'

describe('App', () => {
  it('renders the posts placeholder route', async () => {
    render(
      <MemoryRouter initialEntries={['/posts']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Posts' })).toBeInTheDocument()
  })
})