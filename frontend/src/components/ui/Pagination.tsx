import { Fragment, useEffect, useState, type FormEvent } from 'react'
import { Button } from './Button'
import { Input } from './Input'

interface PaginationProps {
  currentPage: number
  totalPages: number
  onPageChange: (page: number) => void
}

function getVisiblePages(currentPage: number, totalPages: number): number[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, page) => page)
  }

  if (currentPage <= 3) {
    return [0, 1, 2, 3, 4, totalPages - 1]
  }

  if (currentPage >= totalPages - 4) {
    return [0, ...Array.from({ length: 5 }, (_, page) => totalPages - 5 + page)]
  }

  return [0, currentPage - 1, currentPage, currentPage + 1, totalPages - 1]
}

export function Pagination({ currentPage, totalPages, onPageChange }: PaginationProps) {
  const [pageInput, setPageInput] = useState(String(currentPage + 1))
  const [error, setError] = useState('')

  useEffect(() => {
    setPageInput(String(currentPage + 1))
    setError('')
  }, [currentPage])

  if (
    !Number.isSafeInteger(totalPages) ||
    totalPages <= 1 ||
    !Number.isSafeInteger(currentPage) ||
    currentPage < 0 ||
    currentPage >= totalPages
  ) {
    return null
  }

  const visiblePages = getVisiblePages(currentPage, totalPages)
  const changePage = (page: number) => {
    if (Number.isSafeInteger(page) && page >= 0 && page < totalPages) {
      onPageChange(page)
    }
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const value = pageInput.trim()
    if (!value) {
      setError('Enter a page number.')
      return
    }
    if (!/^\d+$/.test(value)) {
      setError('Enter a whole page number.')
      return
    }

    const requestedPage = Number(value)
    if (!Number.isSafeInteger(requestedPage) || requestedPage < 1 || requestedPage > totalPages) {
      setError(`Enter a page number between 1 and ${totalPages}.`)
      return
    }

    setError('')
    setPageInput(String(requestedPage))
    changePage(requestedPage - 1)
  }

  return (
    <nav aria-label="Pagination" className="w-full space-y-3">
      <div className="flex flex-wrap items-center justify-center gap-1">
        <Button
          variant="secondary"
          aria-label="First page"
          disabled={currentPage === 0}
          onClick={() => changePage(0)}
        >
          First
        </Button>
        <Button
          variant="secondary"
          aria-label="Previous page"
          disabled={currentPage === 0}
          onClick={() => changePage(currentPage - 1)}
        >
          Previous
        </Button>

        {visiblePages.map((page, index) => (
          <Fragment key={page}>
            {index > 0 && page - visiblePages[index - 1] > 1 && (
              <span aria-hidden="true" className="px-1 text-lb-muted">…</span>
            )}
            <Button
              variant={page === currentPage ? 'primary' : 'secondary'}
              aria-label={`Page ${page + 1}`}
              aria-current={page === currentPage ? 'page' : undefined}
              onClick={() => changePage(page)}
              className="min-w-10 px-2"
            >
              {page + 1}
            </Button>
          </Fragment>
        ))}

        <Button
          variant="secondary"
          aria-label="Next page"
          disabled={currentPage === totalPages - 1}
          onClick={() => changePage(currentPage + 1)}
        >
          Next
        </Button>
        <Button
          variant="secondary"
          aria-label="Last page"
          disabled={currentPage === totalPages - 1}
          onClick={() => changePage(totalPages - 1)}
        >
          Last
        </Button>
      </div>

      <form
        aria-label="Go to page form"
        className="flex flex-wrap items-end justify-center gap-2"
        onSubmit={handleSubmit}
      >
        <Input
          label="Go to page"
          type="text"
          inputMode="numeric"
          autoComplete="off"
          value={pageInput}
          onChange={(event) => setPageInput(event.target.value)}
          error={error || undefined}
          className="w-24"
        />
        <Button type="submit" variant="secondary">Go</Button>
        {error && <p role="alert" className="sr-only">{error}</p>}
      </form>
    </nav>
  )
}
