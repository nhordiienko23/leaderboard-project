import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { createAdminUser, deleteAdminUser, setAdminUserPassword, updateAdminUser, updateAdminUserRole } from '../../api/admin'
import { normalizeApiError, type ApiError } from '../../api/errors'
import { getUsers } from '../../api/users'
import type { AdminCreateUserRequest, PageResponse, UserResponse, UserRole } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Badge } from '../../components/ui/Badge'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { EmptyState } from '../../components/ui/EmptyState'
import { Input } from '../../components/ui/Input'
import { Pagination } from '../../components/ui/Pagination'
import { Spinner } from '../../components/ui/Spinner'
import { ForbiddenPage } from '../ErrorPages'

const PAGE_SIZE = 20

type UserDialog =
  | { type: 'create' }
  | { type: 'edit' | 'password' | 'role' | 'delete'; user: UserResponse }
  | null

interface UserFormValues {
  username: string
  email: string
  password: string
  role: UserRole
}

const emptyValues: UserFormValues = { username: '', email: '', password: '', role: 'USER' }

function isValidEmail(email: string) {
  return email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

export function AdminDashboardPage() {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<PageResponse<UserResponse> | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<ApiError | null>(null)
  const [retry, setRetry] = useState(0)
  const [dialog, setDialog] = useState<UserDialog>(null)
  const [values, setValues] = useState<UserFormValues>(emptyValues)
  const [dialogError, setDialogError] = useState<ApiError | null>(null)
  const [saving, setSaving] = useState(false)
  const [success, setSuccess] = useState('')
  const requestRef = useRef(0)

  const loadUsers = useCallback(async () => {
    const requestId = ++requestRef.current
    setLoading(true)
    setLoadError(null)
    try {
      const response = await getUsers({ page, size: PAGE_SIZE })
      if (requestRef.current !== requestId) return
      if (page > 0 && page >= response.data.totalPages) {
        setPage(Math.max(0, response.data.totalPages - 1))
        return
      }
      if (requestRef.current === requestId) setResult(response.data)
    } catch (cause) {
      if (requestRef.current === requestId) {
        setLoadError(normalizeApiError(cause))
        setResult(null)
      }
    } finally {
      if (requestRef.current === requestId) setLoading(false)
    }
  }, [page])

  useEffect(() => {
    void loadUsers()
    return () => { requestRef.current += 1 }
  }, [loadUsers, retry])

  const openCreateDialog = () => {
    setValues(emptyValues)
    setDialogError(null)
    setSuccess('')
    setDialog({ type: 'create' })
  }

  const openUserDialog = (type: 'edit' | 'password' | 'role' | 'delete', user: UserResponse) => {
    setValues({
      username: user.username,
      email: user.email,
      password: '',
      role: user.role,
    })
    setDialogError(null)
    setSuccess('')
    setDialog({ type, user })
  }

  const closeDialog = useCallback(() => {
    if (saving) return
    setDialog(null)
    setValues(emptyValues)
    setDialogError(null)
  }, [saving])

  const handleUserSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setDialogError(null)
    setSuccess('')

    const username = values.username.trim()
    const email = values.email.trim()
    if (!username) {
      setDialogError({
        status: null,
        message: 'Username is required.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }
    if (username.length > 50) {
      setDialogError({
        status: null,
        message: 'Username must not exceed 50 characters.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }
    if (!email) {
      setDialogError({
        status: null,
        message: 'Email is required.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }
    if (email.length > 254) {
      setDialogError({
        status: null,
        message: 'Email must not exceed 254 characters.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }
    if (!isValidEmail(email)) {
      setDialogError({
        status: null,
        message: 'Enter a valid email address, for example name@example.com.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }
    if (dialog?.type === 'create' && (!values.password.trim() || values.password.length > 72)) {
      setDialogError({
        status: null,
        message: 'Enter a password of no more than 72 characters.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }

    setSaving(true)
    try {
      if (dialog?.type === 'create') {
        const request: AdminCreateUserRequest = { username, email, password: values.password, role: values.role }
        await createAdminUser(request)
        setSuccess('User created successfully.')
      } else if (dialog?.type === 'edit') {
        await updateAdminUser(dialog.user.id, { username, email })
        setSuccess('User updated successfully.')
      } else if (dialog?.type === 'role') {
        await updateAdminUserRole(dialog.user.id, { role: values.role })
        setSuccess('User role updated successfully.')
      } else {
        return
      }
      setDialog(null)
      setValues(emptyValues)
      setRetry((current) => current + 1)
    } catch (cause) {
      setDialogError(normalizeApiError(cause))
    } finally {
      setSaving(false)
    }
  }

  const handlePasswordSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setDialogError(null)
    setSuccess('')
    if (dialog?.type !== 'password') return
    if (!values.password.trim() || values.password.length > 72) {
      setDialogError({
        status: null,
        message: 'Enter a password of no more than 72 characters.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }

    setSaving(true)
    try {
      await setAdminUserPassword(dialog.user.id, { newPassword: values.password })
      setDialog(null)
      setValues(emptyValues)
      setSuccess(`Password updated for ${dialog.user.username}.`)
    } catch (cause) {
      setDialogError(normalizeApiError(cause))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (dialog?.type !== 'delete') return
    setDialogError(null)
    setSaving(true)
    try {
      await deleteAdminUser(dialog.user.id)
      setDialog(null)
      setSuccess(`${dialog.user.username} was deleted.`)
      setRetry((current) => current + 1)
    } catch (cause) {
      setDialogError(normalizeApiError(cause))
    } finally {
      setSaving(false)
    }
  }

  if (loadError?.status === 403) return <ForbiddenPage />

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">Admin user management</h1>
          <p className="mt-2 text-lb-muted">Create and manage community accounts and roles.</p>
        </div>
        <Button onClick={openCreateDialog}>Create user</Button>
      </header>

      {success && <Alert tone="success" title="Saved">{success}</Alert>}

      {loading && (
        <div className="flex min-h-48 items-center justify-center" role="status" aria-label="Loading admin users">
          <Spinner />
        </div>
      )}

      {!loading && loadError && (
        <div className="space-y-3">
          <Alert tone="error" title="Unable to load users">{loadError.message}</Alert>
          <Button variant="secondary" onClick={() => setRetry((current) => current + 1)}>Retry</Button>
        </div>
      )}

      {!loading && !loadError && result?.content.length === 0 && (
        <EmptyState title="No users found" description="Create an account to get started." />
      )}

      {!loading && !loadError && result && result.content.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-xl border border-lb-border bg-lb-surface">
            <table className="w-full min-w-[60rem] text-left text-sm">
              <thead className="border-b border-lb-border bg-lb-elevated text-xs uppercase tracking-wide text-lb-muted">
                <tr>
                  <th scope="col" className="px-5 py-4">Username</th>
                  <th scope="col" className="px-5 py-4">Email</th>
                  <th scope="col" className="px-5 py-4">Role</th>
                  <th scope="col" className="px-5 py-4">Joined</th>
                  <th scope="col" className="px-5 py-4">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-lb-border">
                {result.content.map((user) => (
                  <tr key={user.id} className="hover:bg-lb-elevated/60">
                    <td className="px-5 py-4 font-medium">{user.username}</td>
                    <td className="px-5 py-4 text-lb-muted">{user.email}</td>
                    <td className="px-5 py-4">
                      <Badge tone={user.role === 'ADMIN' ? 'primary' : 'neutral'}>{user.role}</Badge>
                    </td>
                    <td className="px-5 py-4 text-lb-muted">
                      <time dateTime={user.registeredAt}>{formatDate(user.registeredAt)}</time>
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex flex-wrap gap-2">
                        <Button variant="secondary" className="px-3" aria-label={`Edit ${user.username}`} onClick={() => openUserDialog('edit', user)}>Edit</Button>
                        <Button variant="secondary" className="px-3" aria-label={`Change role for ${user.username}`} onClick={() => openUserDialog('role', user)}>Set Role</Button>
                        <Button variant="secondary" className="px-3" aria-label={`Change password for ${user.username}`} onClick={() => openUserDialog('password', user)}>Set password</Button>
                        <Button variant="danger" className="px-3" aria-label={`Delete ${user.username}`} onClick={() => openUserDialog('delete', user)}>Delete</Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="space-y-3">
            <p className="text-sm text-lb-muted">
              Page {result.number + 1} of {result.totalPages} · {result.totalElements} users
            </p>
            <Pagination currentPage={page} totalPages={result.totalPages} onPageChange={setPage} />
          </div>
        </>
      )}

      <Dialog
        open={dialog?.type === 'create' || dialog?.type === 'edit' || dialog?.type === 'role'}
        title={dialog?.type === 'create' ? 'Create user' : dialog?.type === 'edit' ? `Edit ${dialog.user.username}` : 'Update user role'}
        description={dialog?.type === 'create' ? 'Create a new account with the selected role.' : undefined}
        onClose={closeDialog}
      >
        <form className="space-y-4" onSubmit={(event) => void handleUserSubmit(event)} noValidate>
          {dialogError && <Alert tone="error" title="Unable to save user">{dialogError.message}</Alert>}
          {dialog?.type !== 'role' && (
            <>
              <Input
                label="Username"
                name="username"
                autoComplete="off"
                maxLength={50}
                required
                value={values.username}
                onChange={(event) => setValues((current) => ({ ...current, username: event.target.value }))}
              />
              <Input
                label="Email"
                name="email"
                type="email"
                autoComplete="off"
                helperText="Enter a valid email address, for example name@example.com."
                maxLength={254}
                required
                value={values.email}
                onChange={(event) => setValues((current) => ({ ...current, email: event.target.value }))}
              />
            </>
          )}
          {dialog?.type === 'create' && (
            <Input
              label="Password"
              name="password"
              type="password"
              autoComplete="new-password"
              maxLength={72}
              required
              value={values.password}
              onChange={(event) => setValues((current) => ({ ...current, password: event.target.value }))}
            />
          )}
          {(dialog?.type === 'create' || dialog?.type === 'role') && (
            <label className="flex flex-col gap-2 text-sm font-medium">
              Role
              <select
                aria-label="Role"
                name="role"
                className="h-11 rounded-lg border border-[#2A2F3D] bg-[#171A23] px-3 text-[#F4F4F5]"
                value={values.role}
                onChange={(event) => setValues((current) => ({
                  ...current,
                  role: event.target.value === 'ADMIN' ? 'ADMIN' : 'USER',
                }))}
              >
                <option value="USER">USER</option>
                <option value="ADMIN">ADMIN</option>
              </select>
            </label>
          )}
          <div className="flex justify-end gap-3">
            <Button variant="secondary" onClick={closeDialog} disabled={saving}>Cancel</Button>
            <Button type="submit" loading={saving}>
              {dialog?.type === 'create' ? 'Create user' : dialog?.type === 'role' ? 'Save role' : 'Save changes'}
            </Button>
          </div>
        </form>
      </Dialog>

      <Dialog
        open={dialog?.type === 'password'}
        title={dialog?.type === 'password' ? `Change password for ${dialog.user.username}` : 'Change password'}
        description="The new password can contain up to 72 characters."
        onClose={closeDialog}
      >
        <form className="space-y-4" onSubmit={(event) => void handlePasswordSubmit(event)} noValidate>
          {dialogError && <Alert tone="error" title="Unable to change password">{dialogError.message}</Alert>}
          <Input
            label="New password"
            name="newPassword"
            type="password"
            autoComplete="new-password"
            maxLength={72}
            required
            value={values.password}
            onChange={(event) => setValues((current) => ({ ...current, password: event.target.value }))}
          />
          <div className="flex justify-end gap-3">
            <Button variant="secondary" onClick={closeDialog} disabled={saving}>Cancel</Button>
            <Button type="submit" loading={saving}>Set password</Button>
          </div>
        </form>
      </Dialog>

      <Dialog
        open={dialog?.type === 'delete'}
        title={dialog?.type === 'delete' ? `Delete ${dialog.user.username}?` : 'Delete user?'}
        description="This permanently deletes the selected account. This action cannot be undone."
        onClose={closeDialog}
      >
        <div className="space-y-4">
          {dialogError && <Alert tone="error" title="Unable to delete user">{dialogError.message}</Alert>}
          <div className="flex justify-end gap-3">
            <Button variant="secondary" onClick={closeDialog} disabled={saving}>Cancel</Button>
            <Button variant="danger" loading={saving} onClick={() => void handleDelete()}>Delete user</Button>
          </div>
        </div>
      </Dialog>
    </section>
  )
}
