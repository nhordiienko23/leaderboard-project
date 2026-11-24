import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Dialog } from './Dialog'

describe('Dialog', () => {
  it('uses dialog semantics and an accessible close label', () => {
    render(
      <Dialog open title="Confirm deletion" description="This cannot be undone" onClose={vi.fn()}>
        <button type="button">Delete</button>
      </Dialog>,
    )

    expect(screen.getByRole('dialog', { name: 'Confirm deletion' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Close dialog' })).toBeInTheDocument()
  })

  it('closes with Escape', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()

    render(
      <Dialog open title="Settings" onClose={onClose}>
        <button type="button">Save</button>
      </Dialog>,
    )

    await user.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('focuses the first control when opened', () => {
    render(
      <Dialog open title="Settings" onClose={vi.fn()}>
        <button type="button">Save</button>
      </Dialog>,
    )

    expect(screen.getByRole('button', { name: 'Close dialog' })).toHaveFocus()
  })
})

