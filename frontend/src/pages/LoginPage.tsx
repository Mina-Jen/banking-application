import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Alert } from '../components/Alert'
import { AuthLayout } from '../components/AuthLayout'
import { Field } from '../components/Field'
import { Spinner } from '../components/Spinner'
import { buttonPrimary } from '../components/ui'
import { ApiError, errorMessage } from '../api/errors'
import { useAuth } from '../auth/AuthContext'

interface LocationState {
  /** Set by RequireAuth: where to go back to after signing in. */
  from?: string
  /** Set by the register page when the account was created but sign-in failed. */
  notice?: string
}

/** Turns the backend's Retry-After seconds into a human wait time. */
function retryAfterText(seconds: number): string {
  if (seconds <= 60) {
    return `Try again in ${seconds} seconds.`
  }
  return `Try again in ${Math.ceil(seconds / 60)} minutes.`
}

export function LoginPage() {
  const { isAuthenticated, signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = (location.state ?? {}) as LocationState

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Already signed in: the login page has nothing to offer.
  if (isAuthenticated) {
    return <Navigate to={state.from ?? '/'} replace />
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) {
      return
    }

    setError(null)
    setSubmitting(true)
    try {
      await signIn(username.trim(), password)
      // signIn already stored the tokens, so the re-render above has happened;
      // send the user where RequireAuth caught them, else to the dashboard.
      navigate(state.from ?? '/', { replace: true })
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 429 && cause.retryAfterSeconds) {
        setError(`${cause.message} ${retryAfterText(cause.retryAfterSeconds)}`)
      } else {
        setError(errorMessage(cause))
      }
      setPassword('')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Sign in"
      subtitle="Use the login you registered with."
      footer={
        <>
          No account yet?{' '}
          <Link to="/register" className="font-medium text-emerald-700 hover:text-emerald-800">
            Register
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {state.notice ? <Alert tone="success">{state.notice}</Alert> : null}
        {error ? <Alert tone="error">{error}</Alert> : null}

        <Field
          label="Username"
          name="username"
          value={username}
          autoComplete="username"
          autoFocus
          required
          onChange={(event) => setUsername(event.target.value)}
        />

        <Field
          label="Password"
          name="password"
          type="password"
          value={password}
          autoComplete="current-password"
          required
          onChange={(event) => setPassword(event.target.value)}
        />

        <button type="submit" className={buttonPrimary} disabled={submitting || !username || !password}>
          {submitting ? <Spinner /> : null}
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </AuthLayout>
  )
}
