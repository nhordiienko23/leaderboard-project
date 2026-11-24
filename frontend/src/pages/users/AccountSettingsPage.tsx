import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { normalizeApiError, type ApiError } from '../../api/errors'
import { changeMyPassword, deleteMyAccount, getUser, updateMyProfile } from '../../api/users'
import type { UserResponse } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { Dialog } from '../../components/ui/Dialog'
import { Input } from '../../components/ui/Input'
import { Spinner } from '../../components/ui/Spinner'

interface ProfileValues {
  username: string
  email: string
}

interface PasswordValues {
  currentPassword: string
  newPassword: string
}

const emptyPassword: PasswordValues = { currentPassword: '', newPassword: '' }

function passwordByteLength(value: string) {
  return new TextEncoder().encode(value).length
}

export function AccountSettingsPage() {
  const { claims, logout } = useAuth()
  const navigate = useNavigate()
  const [profile, setProfile] = useState<UserResponse | null>(null)
  const [profileValues, setProfileValues] = useState<ProfileValues>({ username: '', email: '' })
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<ApiError | null>(null)
  const [profileError, setProfileError] = useState<ApiError | null>(null)
  const [profileSuccess, setProfileSuccess] = useState('')
  const [profileSaving, setProfileSaving] = useState(false)
  const [passwordValues, setPasswordValues] = useState<PasswordValues>(emptyPassword)
  const [passwordError, setPasswordError] = useState<ApiError | null>(null)
  const [passwordSuccess, setPasswordSuccess] = useState('')
  const [passwordSaving, setPasswordSaving] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleteConfirmation, setDeleteConfirmation] = useState('')
  const [deleteError, setDeleteError] = useState<ApiError | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [retry, setRetry] = useState(0)
  const requestRef = useRef(0)

  const loadProfile = useCallback(async () => {
    const requestId = ++requestRef.current
    setLoading(true)
    setLoadError(null)
    if (!claims?.sub) {
      setLoadError({
        status: null,
        message: 'Your account identity is unavailable. Please sign in again.',
        fieldErrors: {},
        isNetworkError: false,
      })
      setLoading(false)
      return
    }

    try {
      const response = await getUser(claims.sub)
      if (requestRef.current === requestId) {
        setProfile(response.data)
        setProfileValues({ username: response.data.username, email: response.data.email })
      }
    } catch (cause) {
      if (requestRef.current === requestId) {
        setLoadError(normalizeApiError(cause))
        setProfile(null)
      }
    } finally {
      if (requestRef.current === requestId) setLoading(false)
    }
  }, [claims?.sub])

  useEffect(() => {
    void loadProfile()
    return () => { requestRef.current += 1 }
  }, [loadProfile, retry])

  const handleProfileSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setProfileError(null)
    setProfileSuccess('')

    const username = profileValues.username.trim()
    const email = profileValues.email.trim()
    if (!username) {
      setProfileError({
        status: null,
        message: 'Username is required.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }
    if (username.length > 50) {
      setProfileError({
        status: null,
        message: 'Username must not exceed 50 characters.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }
    if (!email) {
      setProfileError({
        status: null,
        message: 'Email is required.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }
    if (email.length > 254) {
      setProfileError({
        status: null,
        message: 'Email must not exceed 254 characters.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setProfileError({
        status: null,
        message: 'Enter a valid email address, for example name@example.com.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }

    setProfileSaving(true)
    try {
      const response = await updateMyProfile({ username, email })
      setProfile(response.data)
      setProfileValues({ username: response.data.username, email: response.data.email })
      setProfileSuccess('Profile updated successfully.')
    } catch (cause) {
      setProfileError(normalizeApiError(cause))
    } finally {
      setProfileSaving(false)
    }
  }

  const handlePasswordSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setPasswordError(null)
    setPasswordSuccess('')

    const { currentPassword, newPassword } = passwordValues
    if (!currentPassword.trim() || !newPassword.trim()) {
      setPasswordError({
        status: null,
        message: 'Enter your current password and a new password.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }
    if (passwordByteLength(currentPassword) > 72 || passwordByteLength(newPassword) > 72) {
      setPasswordError({
        status: null,
        message: 'Passwords must not exceed 72 UTF-8 bytes.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }
    if (currentPassword === newPassword) {
      setPasswordError({
        status: null,
        message: 'The new password must be different from the current password.',
        fieldErrors: {},
        isNetworkError: false,
      })
      return
    }

    setPasswordSaving(true)
    try {
      await changeMyPassword({ currentPassword, newPassword })
      setPasswordValues(emptyPassword)
      setPasswordSuccess('Password changed successfully.')
    } catch (cause) {
      setPasswordError(normalizeApiError(cause))
    } finally {
      setPasswordSaving(false)
    }
  }

  const closeDeleteDialog = useCallback(() => {
    if (deleting) return
    setDeleteOpen(false)
    setDeleteConfirmation('')
    setDeleteError(null)
  }, [deleting])

  const handleDeleteAccount = async () => {
    setDeleteError(null)
    setDeleting(true)
    try {
      await deleteMyAccount()
      await logout()
      navigate('/login', { replace: true })
    } catch (cause) {
      setDeleteError(normalizeApiError(cause))
    } finally {
      setDeleting(false)
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-48 items-center justify-center" role="status" aria-label="Loading account settings">
        <Spinner />
      </div>
    )
  }

  if (loadError || !profile) {
    return (
      <div className="space-y-3">
        <Alert tone="error" title="Unable to load account settings">
          {loadError?.message ?? 'Your profile could not be loaded.'}
        </Alert>
        <Button variant="secondary" onClick={() => setRetry((current) => current + 1)}>Retry</Button>
      </div>
    )
  }

  return (
    <section className="mx-auto max-w-3xl space-y-6">
      <header>
        <h1 className="text-2xl font-bold">Account settings</h1>
        <p className="mt-1 text-sm text-lb-muted">Manage your profile, password, and account.</p>
      </header>

      <Card className="p-5 sm:p-6">
        <h2 className="text-lg font-semibold">Profile</h2>
        <form className="mt-5 space-y-4" onSubmit={(event) => void handleProfileSubmit(event)} noValidate>
          {profileError && <Alert tone="error" title="Profile update failed">{profileError.message}</Alert>}
          {profileSuccess && <Alert tone="success" title="Saved">{profileSuccess}</Alert>}
          <Input
            label="Username"
            name="username"
            autoComplete="username"
            maxLength={50}
            required
            value={profileValues.username}
            onChange={(event) => setProfileValues((values) => ({ ...values, username: event.target.value }))}
          />
          <Input
            label="Email"
            name="email"
            type="email"
            autoComplete="email"
            helperText="Enter a valid email address, for example name@example.com."
            maxLength={254}
            required
            value={profileValues.email}
            onChange={(event) => setProfileValues((values) => ({ ...values, email: event.target.value }))}
          />
          <Button type="submit" loading={profileSaving}>Save profile</Button>
        </form>
      </Card>

      <Card className="p-5 sm:p-6">
        <h2 className="text-lg font-semibold">Change password</h2>
        <form className="mt-5 space-y-4" onSubmit={(event) => void handlePasswordSubmit(event)} noValidate>
          {passwordError && <Alert tone="error" title="Password change failed">{passwordError.message}</Alert>}
          {passwordSuccess && <Alert tone="success" title="Changed">{passwordSuccess}</Alert>}
          <Input
            label="Current password"
            name="currentPassword"
            type="password"
            autoComplete="current-password"
            maxLength={72}
            required
            value={passwordValues.currentPassword}
            onChange={(event) => setPasswordValues((values) => ({ ...values, currentPassword: event.target.value }))}
          />
          <Input
            label="New password"
            name="newPassword"
            type="password"
            autoComplete="new-password"
            maxLength={72}
            required
            value={passwordValues.newPassword}
            onChange={(event) => setPasswordValues((values) => ({ ...values, newPassword: event.target.value }))}
          />
          <Button type="submit" loading={passwordSaving}>Change password</Button>
        </form>
      </Card>

      <Card className="border-red-900/60 p-5 sm:p-6">
        <h2 className="text-lg font-semibold text-red-400">Delete account</h2>
        <p className="mt-2 text-sm text-lb-muted">This permanently deletes your account and signs you out.</p>
        <Button className="mt-4" variant="danger" onClick={() => setDeleteOpen(true)}>Delete account</Button>
      </Card>

      <Dialog
        open={deleteOpen}
        title="Delete your account?"
        description="This action cannot be undone. Your account will be permanently deleted and you will be signed out."
        onClose={closeDeleteDialog}
      >
        <div className="space-y-4">
          {deleteError && <Alert tone="error" title="Account deletion failed">{deleteError.message}</Alert>}
          <Input
            label="Type DELETE to confirm"
            value={deleteConfirmation}
            onChange={(event) => setDeleteConfirmation(event.target.value)}
            autoComplete="off"
          />
          <div className="flex justify-end gap-3">
            <Button variant="secondary" onClick={closeDeleteDialog} disabled={deleting}>Cancel</Button>
            <Button
              variant="danger"
              loading={deleting}
              disabled={deleteConfirmation !== 'DELETE'}
              onClick={() => void handleDeleteAccount()}
            >
              Permanently delete
            </Button>
          </div>
        </div>
      </Dialog>
    </section>
  )
}
