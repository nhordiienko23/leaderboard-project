import { useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { Alert, Button, Card, Input } from '../../components/ui'
import { getAuthError, passwordError } from './authForm'

type FieldErrors = Partial<Record<'username' | 'email' | 'password', string>>

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [values, setValues] = useState({ username: '', email: '', password: '' })
  const [errors, setErrors] = useState<FieldErrors>({})
  const [error, setError] = useState<string>()
  const [pending, setPending] = useState(false)
  const submitting = useRef(false)

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (submitting.current) return

    const username = values.username.trim()
    const email = values.email.trim()
    const fieldErrors: FieldErrors = {
      username: !username ? 'Username is required.'
        : values.username.length > 50 ? 'Username must not exceed 50 characters.' : undefined,
      email: !email ? 'Email is required.'
        : values.email.length > 254 ? 'Email must not exceed 254 characters.'
          : !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
            ? 'Enter a valid email address, for example name@example.com.' : undefined,
      password: passwordError(values.password),
    }
    setErrors(fieldErrors)
    setError(undefined)
    if (fieldErrors.username || fieldErrors.email || fieldErrors.password) return

    submitting.current = true
    setPending(true)
    try {
      await register({ username, email, password: values.password })
      navigate('/login', { replace: true })
    } catch (cause) {
      const apiError = getAuthError(cause)
      setErrors({
        username: apiError.fieldErrors.username,
        email: apiError.fieldErrors.email,
        password: apiError.fieldErrors.password,
      })
      setError(apiError.message)
    } finally {
      submitting.current = false
      setPending(false)
    }
  }

  return (
    <Card className="mx-auto max-w-md p-6 sm:p-8">
      <h1 className="text-3xl font-bold">Register</h1>
      <p className="mt-2 text-sm text-lb-muted">Create your community account.</p>
      <form className="mt-6 space-y-4" onSubmit={(event) => void submit(event)} noValidate>
        {error && <Alert tone="error" title="Registration failed">{error}</Alert>}
        <Input
          label="Username"
          name="username"
          autoComplete="username"
          maxLength={50}
          required
          disabled={pending}
          value={values.username}
          error={errors.username}
          onChange={(event) => {
            setValues((current) => ({ ...current, username: event.target.value }))
            setErrors((current) => ({ ...current, username: undefined }))
          }}
        />
        <Input
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          helperText="Enter a valid email address, for example name@example.com."
          maxLength={254}
          required
          disabled={pending}
          value={values.email}
          error={errors.email}
          onChange={(event) => {
            setValues((current) => ({ ...current, email: event.target.value }))
            setErrors((current) => ({ ...current, email: undefined }))
          }}
        />
        <Input
          label="Password"
          name="password"
          type="password"
          autoComplete="new-password"
          maxLength={72}
          required
          disabled={pending}
          value={values.password}
          error={errors.password}
          onChange={(event) => {
            setValues((current) => ({ ...current, password: event.target.value }))
            setErrors((current) => ({ ...current, password: undefined }))
          }}
        />
        <Button type="submit" className="w-full" loading={pending}>Register</Button>
      </form>
      <p className="mt-6 text-sm text-lb-muted">
        Already have an account? <Link to="/login" className="text-lb-primary-hover hover:underline">Login</Link>
      </p>
    </Card>
  )
}
