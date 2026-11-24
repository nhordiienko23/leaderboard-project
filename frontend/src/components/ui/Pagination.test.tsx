import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Pagination } from './Pagination'

describe('Pagination', () => {
  it('renders labeled navigation controls and marks the one-based current page', () => {
    render(<Pagination currentPage={1} totalPages={3} onPageChange={vi.fn()} />)

    expect(screen.getByRole('navigation', { name: 'Pagination' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'First page' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next page' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Last page' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveTextContent('2')
    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('textbox', { name: 'Go to page' })).toBeInTheDocument()
  })

  it('navigates to first, previous, next, last, and numbered pages using zero-based indexes', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(<Pagination currentPage={2} totalPages={5} onPageChange={onPageChange} />)

    await user.click(screen.getByRole('button', { name: 'First page' }))
    await user.click(screen.getByRole('button', { name: 'Previous page' }))
    await user.click(screen.getByRole('button', { name: 'Next page' }))
    await user.click(screen.getByRole('button', { name: 'Last page' }))
    await user.click(screen.getByRole('button', { name: 'Page 2' }))

    expect(onPageChange.mock.calls).toEqual([[0], [1], [3], [4], [1]])
  })

  it('disables boundary controls and does not navigate when they are activated', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    const { rerender } = render(
      <Pagination currentPage={0} totalPages={3} onPageChange={onPageChange} />,
    )

    const firstPage = screen.getByRole('button', { name: 'First page' })
    const previousPage = screen.getByRole('button', { name: 'Previous page' })
    expect(firstPage).toBeDisabled()
    expect(previousPage).toBeDisabled()
    await user.click(firstPage)
    await user.click(previousPage)
    expect(onPageChange).not.toHaveBeenCalled()

    rerender(<Pagination currentPage={2} totalPages={3} onPageChange={onPageChange} />)
    const nextPage = screen.getByRole('button', { name: 'Next page' })
    const lastPage = screen.getByRole('button', { name: 'Last page' })
    expect(nextPage).toBeDisabled()
    expect(lastPage).toBeDisabled()
    await user.click(nextPage)
    await user.click(lastPage)
    expect(onPageChange).not.toHaveBeenCalled()
  })

  it('shows all pages for small results without duplicates', () => {
    render(<Pagination currentPage={0} totalPages={5} onPageChange={vi.fn()} />)

    const pageButtons = screen.getAllByRole('button').filter((button) =>
      /^Page \d+$/.test(button.getAttribute('aria-label') ?? ''),
    )
    expect(pageButtons.map((button) => button.textContent)).toEqual(['1', '2', '3', '4', '5'])
    expect(screen.queryByText('…')).not.toBeInTheDocument()
  })

  it('bounds large page ranges, keeps the current page visible, and uses ellipses only for gaps', () => {
    const { rerender } = render(
      <Pagination currentPage={0} totalPages={100} onPageChange={vi.fn()} />,
    )

    let pageButtons = screen.getAllByRole('button').filter((button) =>
      /^Page \d+$/.test(button.getAttribute('aria-label') ?? ''),
    )
    expect(pageButtons).toHaveLength(6)
    expect(pageButtons.map((button) => button.textContent)).toEqual(['1', '2', '3', '4', '5', '100'])
    expect(screen.getAllByText('…')).toHaveLength(1)

    rerender(<Pagination currentPage={50} totalPages={100} onPageChange={vi.fn()} />)
    pageButtons = screen.getAllByRole('button').filter((button) =>
      /^Page \d+$/.test(button.getAttribute('aria-label') ?? ''),
    )
    expect(pageButtons).toHaveLength(5)
    expect(pageButtons.map((button) => button.textContent)).toEqual(['1', '50', '51', '52', '100'])
    expect(screen.getAllByText('…')).toHaveLength(2)
    expect(screen.getByRole('button', { name: 'Page 51' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: 'Page 1' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Page 100' })).toBeInTheDocument()

    rerender(<Pagination currentPage={99} totalPages={100} onPageChange={vi.fn()} />)
    expect(screen.getAllByText('…')).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Page 100' })).toBeInTheDocument()
  })

  it('does not present ellipses as interactive controls', () => {
    render(<Pagination currentPage={50} totalPages={100} onPageChange={vi.fn()} />)

    const ellipses = screen.getAllByText('…')
    expect(ellipses).toHaveLength(2)
    for (const ellipsis of ellipses) {
      expect(ellipsis.tagName).not.toBe('BUTTON')
      expect(ellipsis).toHaveAttribute('aria-hidden', 'true')
    }
  })

  it('submits a valid page number through the Go button and clears an earlier error', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(<Pagination currentPage={0} totalPages={8} onPageChange={onPageChange} />)

    const input = screen.getByRole('textbox', { name: 'Go to page' })
    await user.clear(input)
    await user.click(screen.getByRole('button', { name: 'Go' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Enter a page number.')
    expect(input).toHaveAttribute('aria-invalid', 'true')

    await user.type(input, '6')
    await user.click(screen.getByRole('button', { name: 'Go' }))
    expect(onPageChange).toHaveBeenCalledWith(5)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(input).toHaveValue('6')
  })

  it('submits valid input with Enter', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(<Pagination currentPage={0} totalPages={10} onPageChange={onPageChange} />)

    const input = screen.getByRole('textbox', { name: 'Go to page' })
    await user.clear(input)
    await user.type(input, '3{Enter}')

    expect(onPageChange).toHaveBeenCalledWith(2)
  })

  it.each([
    ['', 'Enter a page number.'],
    ['abc', 'Enter a whole page number.'],
    ['2.5', 'Enter a whole page number.'],
    ['-1', 'Enter a whole page number.'],
    ['0', 'Enter a page number between 1 and 5.'],
    ['6', 'Enter a page number between 1 and 5.'],
  ])('rejects invalid direct page input %j with accessible feedback', async (value, message) => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(<Pagination currentPage={0} totalPages={5} onPageChange={onPageChange} />)

    const input = screen.getByRole('textbox', { name: 'Go to page' })
    await user.clear(input)
    if (value) await user.type(input, value)
    await user.click(screen.getByRole('button', { name: 'Go' }))

    expect(onPageChange).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent(message)
    expect(input).toHaveAccessibleDescription(message)
    expect(input).toHaveAttribute('aria-invalid', 'true')
  })

  it('hides navigation for zero or one page and for invalid pagination props', () => {
    const onPageChange = vi.fn()
    const { rerender, container } = render(
      <Pagination currentPage={0} totalPages={0} onPageChange={onPageChange} />,
    )
    expect(container).toBeEmptyDOMElement()

    rerender(<Pagination currentPage={0} totalPages={1} onPageChange={onPageChange} />)
    expect(container).toBeEmptyDOMElement()

    rerender(<Pagination currentPage={2} totalPages={2} onPageChange={onPageChange} />)
    expect(container).toBeEmptyDOMElement()

    rerender(<Pagination currentPage={0} totalPages={Number.NaN} onPageChange={onPageChange} />)
    expect(container).toBeEmptyDOMElement()
    expect(onPageChange).not.toHaveBeenCalled()
  })

  it('keeps controls keyboard-operable and wrapped for narrow layouts', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(<Pagination currentPage={4} totalPages={10} onPageChange={onPageChange} />)

    const navigation = screen.getByRole('navigation', { name: 'Pagination' })
    expect(within(navigation).getByRole('button', { name: 'Next page' })).toHaveClass('min-h-10')
    expect(navigation.querySelector('.flex.flex-wrap')).not.toBeNull()
    expect(screen.getByRole('textbox', { name: 'Go to page' })).toHaveAccessibleName('Go to page')

    await user.tab()
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'First page' }))
  })
})
