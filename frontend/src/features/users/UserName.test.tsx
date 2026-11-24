import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../api/client'
import { updateMyProfile } from '../../api/users'
import { updateAdminUser } from '../../api/admin'
import { UserName } from './UserName'
import { updateUserSummary } from '../../api/userSummaries'

afterEach(() => vi.restoreAllMocks())

describe('database-backed usernames', () => {
  it('shares concurrent author requests and renders the actual name rather than an ID', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { id: 501, username: 'q' } } as never)
    render(<><UserName userId={501} /><UserName userId={501} /></>)
    await waitFor(() => expect(screen.getAllByText('q')).toHaveLength(2))
    expect(get).toHaveBeenCalledExactlyOnceWith('/api/users/501/summary')
    expect(screen.queryByText('User #501')).not.toBeInTheDocument()
  })

  it('reuses a successful summary after unmount and remount', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { id: 510, username: 'Cached' } } as never)
    const first = render(<UserName userId={510} />)
    await screen.findByText('Cached')
    first.unmount()

    render(<UserName userId={510} />)
    expect(screen.getByText('Cached')).toBeInTheDocument()
    expect(get).toHaveBeenCalledExactlyOnceWith('/api/users/510/summary')
  })

  it('uses the database name in leaderboard profile links', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { id: 502, username: 'Ada' } } as never)
    render(<MemoryRouter><UserName userId={502} profileLink /></MemoryRouter>)
    expect(await screen.findByRole('link', { name: 'View profile for Ada' }))
      .toHaveAttribute('href', '/users/502')
  })

  it('updates mounted names after a successful account or admin profile edit', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { id: 503, username: 'Before' } } as never)
    const patch = vi.spyOn(apiClient, 'patch')
      .mockResolvedValueOnce({ data: { id: 503, username: 'After' } } as never)
      .mockResolvedValueOnce({ data: { id: 503, username: 'Admin edit' } } as never)
    render(<UserName userId={503} />)
    await screen.findByText('Before')
    await act(async () => { await updateMyProfile({ username: 'After' }) })
    expect(screen.getByText('After')).toBeInTheDocument()
    await act(async () => { await updateAdminUser(503, { username: 'Admin edit' }) })
    expect(screen.getByText('Admin edit')).toBeInTheDocument()
    expect(patch).toHaveBeenCalledTimes(2)
  })

  it('shows a retryable error and recovers without inventing a name', async () => {
    const get = vi.spyOn(apiClient, 'get')
      .mockRejectedValueOnce(new Error('Service unavailable'))
      .mockResolvedValueOnce({ data: { id: 504, username: 'Recovered' } } as never)
    render(<UserName userId={504} />)
    const retry = await screen.findByRole('button', { name: 'Retry username' })
    expect(retry).toHaveAttribute('title', 'Service unavailable')
    await userEvent.click(retry)
    expect(await screen.findByText('Recovered')).toBeInTheDocument()
    expect(get).toHaveBeenCalledTimes(2)
  })

  it('identifies deleted authors explicitly', async () => {
    vi.spyOn(apiClient, 'get').mockRejectedValue({
      isAxiosError: true, response: { status: 404, data: { message: 'User not found' } },
    })
    render(<UserName userId={505} />)
    expect(await screen.findByText('Deleted user')).toBeInTheDocument()
  })

  it('reloads the summary when the author ID changes', async () => {
    const get = vi.spyOn(apiClient, 'get')
      .mockResolvedValueOnce({ data: { id: 506, username: 'First' } } as never)
      .mockResolvedValueOnce({ data: { id: 507, username: 'Second' } } as never)
    const view = render(<UserName userId={506} />)
    await screen.findByText('First')
    view.rerender(<UserName userId={507} />)
    expect(await screen.findByText('Second')).toBeInTheDocument()
    expect(screen.queryByText('First')).not.toBeInTheDocument()
    expect(get).toHaveBeenLastCalledWith('/api/users/507/summary')
  })

  it('does not overwrite a profile edit with an older in-flight lookup', async () => {
    let resolve!: (response: { data: { id: number; username: string } }) => void
    vi.spyOn(apiClient, 'get').mockReturnValue(new Promise((done) => { resolve = done }) as never)
    render(<UserName userId={508} />)
    act(() => updateUserSummary({ id: 508, username: 'Renamed' }))
    await act(async () => { resolve({ data: { id: 508, username: 'Old name' } }) })
    expect(screen.getByText('Renamed')).toBeInTheDocument()
    expect(screen.queryByText('Old name')).not.toBeInTheDocument()
  })

  it('reports malformed summaries rather than showing another user name', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { id: 999, username: 'Wrong user' } } as never)
    render(<UserName userId={509} />)
    expect(await screen.findByRole('button', { name: 'Retry username' }))
      .toHaveAttribute('title', 'The user service returned an invalid author summary.')
    expect(screen.queryByText('Wrong user')).not.toBeInTheDocument()
  })
})
