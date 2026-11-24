import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Alert } from './Alert'
import { Badge } from './Badge'
import { EmptyState } from './EmptyState'
import { Spinner } from './Spinner'

describe('feedback components', () => {
  it('renders an alert with an appropriate error role', () => {
    render(<Alert tone="error" title="Network error">Try again</Alert>)

    expect(screen.getByRole('alert')).toHaveTextContent('Network error')
  })

  it('renders badge and empty state content', () => {
    render(
      <>
        <Badge tone="success">Active</Badge>
        <EmptyState title="No posts" description="Start the conversation" />
      </>,
    )

    expect(screen.getByText('Active')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'No posts' })).toBeInTheDocument()
  })

  it('exposes spinner loading status', () => {
    render(<Spinner label="Loading posts" />)

    expect(screen.getByRole('status')).toHaveTextContent('Loading posts')
  })
})

