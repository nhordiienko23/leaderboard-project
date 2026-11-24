import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Input } from './Input'

describe('Input', () => {
  it('connects its label and helper text', () => {
    render(<Input label="Email" helperText="We use this for login" />)

    const input = screen.getByRole('textbox', { name: 'Email' })
    expect(input).toHaveAccessibleDescription('We use this for login')
  })

  it('exposes an error state', () => {
    render(<Input label="Username" error="Username is required" />)

    const input = screen.getByRole('textbox', { name: 'Username' })
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription('Username is required')
  })

  it('keeps helper guidance accessible alongside a distinct error', () => {
    render(<Input
      label="Email"
      helperText="Enter a valid email address, for example name@example.com."
      error="Email is already in use."
    />)

    const input = screen.getByRole('textbox', { name: 'Email' })
    expect(input).toHaveAccessibleDescription(
      'Enter a valid email address, for example name@example.com. Email is already in use.',
    )
    expect(screen.getByText('Enter a valid email address, for example name@example.com.')).toBeInTheDocument()
    expect(screen.getByText('Email is already in use.')).toBeInTheDocument()
  })

  it('can be disabled', () => {
    render(<Input label="Account" disabled />)

    expect(screen.getByRole('textbox', { name: 'Account' })).toBeDisabled()
  })
})
