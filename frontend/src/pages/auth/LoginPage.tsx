import { useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext'
import { Alert, Button, Card, Input } from '../../components/ui'
import { getAuthError, passwordError } from './authForm'

type FieldErrors = Partial<Record<'login' | 'password', string>>

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [values, setValues] = useState({ login: '', password: '' })
  const [errors, setErrors] = useState<FieldErrors>({})
  const [error, setError] = useState<string>()
  const [pending, setPending] = useState(false)
  const submitting = useRef(false)

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (submitting.current) return

    const fieldErrors: FieldErrors = {
      login: !values.login.trim() ? 'Username or email is required.'
        : values.login.length > 254 ? 'Username or email must not exceed 254 characters.' : undefined,
      password: passwordError(values.password),
    }
    setErrors(fieldErrors)
    setError(undefined)
    if (fieldErrors.login || fieldErrors.password) return

    submitting.current = true
    setPending(true)
    try {
      await login({ login: values.login, password: values.password })
      navigate('/posts', { replace: true })
    } catch (cause) {
      const apiError = getAuthError(cause)
      setErrors({ login: apiError.fieldErrors.login, password: apiError.fieldErrors.password })
      setError(apiError.message)
    } finally {
      submitting.current = false
      setPending(false)
    }
  }

  return (
    <Card className="mx-auto max-w-md p-6 sm:p-8">
      <h1 className="text-3xl font-bold">Login</h1>
      <p className="mt-2 text-sm text-lb-muted">Sign in to your account.</p>
      <form className="mt-6 space-y-4" onSubmit={(event) => void submit(event)} noValidate>
        {error && <Alert tone="error" title="Login failed">{error}</Alert>}
        <Input
          label="Username or email"
          name="login"
          autoComplete="username"
          maxLength={254}
          required
          disabled={pending}
          value={values.login}
          error={errors.login}
          onChange={(event) => {
            setValues((current) => ({ ...current, login: event.target.value }))
            setErrors((current) => ({ ...current, login: undefined }))
          }}
        />
        <Input
          label="Password"
          name="password"
          type="password"
          autoComplete="current-password"
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
        <Button type="submit" className="w-full" loading={pending}>Login</Button>
      </form>
      <p className="mt-6 text-sm text-lb-muted">
        New here? <Link to="/register" className="text-lb-primary-hover hover:underline">Register</Link>
      </p>
    </Card>
  )
}
