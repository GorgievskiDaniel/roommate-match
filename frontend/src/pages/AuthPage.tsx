import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api'
import { useAuth } from '../auth'
import AuthLayout from '../components/AuthLayout'

const RESENT = 'Ако акаунтот уште не е потврден, ти испративме нова врска.'

export default function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { signIn } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null)
  const [unconfirmed, setUnconfirmed] = useState(false)
  const [resent, setResent] = useState<string | null>(null)
  const registering = mode === 'register'

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    setUnconfirmed(false)
    setResent(null)
    if (registering && password !== confirmation) {
      setFieldErrors({ confirmation: 'Лозинките не се совпаѓаат.' })
      return
    }
    setSubmitting(true)
    try {
      if (registering) {
        const created = await api<{ email: string }>('/auth/register', { method: 'POST', body: { email, password } })
        setRegisteredEmail(created.email)
      } else {
        await api('/auth/login', { method: 'POST', body: { email, password } })
        await signIn()
      }
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message)
        setFieldErrors(caught.fieldErrors)
        setUnconfirmed(caught.code === 'EMAIL_NOT_VERIFIED')
      } else {
        setError('Настана грешка. Обидете се повторно.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function resend(address: string) {
    setResent(null)
    try {
      await api('/auth/resend-confirmation', { method: 'POST', body: { email: address } })
      setResent(RESENT)
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Настана грешка. Обидете се повторно.')
    }
  }

  if (registeredEmail) {
    return (
      <AuthLayout>
        <div className="card auth-card">
          <h2>Провери ја е-поштата</h2>
          <p>
            Ти испративме врска на <strong>{registeredEmail}</strong>. Отвори ја за да го активираш акаунтот. Врската
            важи 24 часа.
          </p>
          <p className="muted">Ако не ја гледаш, провери ја папката за спам.</p>
          {resent && <p role="status" className="form-success">{resent}</p>}
          {error && <p role="alert" className="form-error">{error}</p>}
          <button type="button" className="button secondary" onClick={() => void resend(registeredEmail)}>
            Испрати ја врската повторно
          </button>
          <p className="muted auth-switch">
            <Link to="/login">Назад кон најава</Link>
          </p>
        </div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout>
      <form className={registering ? 'card auth-card auth-register' : 'card auth-card'} onSubmit={submit} noValidate>
        <h2>{registering ? 'Создај акаунт' : 'Најави се'}</h2>
        {error && <p role="alert" className="form-error">{error}</p>}
        {unconfirmed && (
          <p className="auth-resend">
            <button type="button" className="link-button" onClick={() => void resend(email)}>
              Испрати ја врската повторно
            </button>
          </p>
        )}
        {resent && <p role="status" className="form-success">{resent}</p>}
        <label className="field">
          <span>Е-пошта</span>
          <input
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={Boolean(fieldErrors.email)}
          />
          {fieldErrors.email && <small className="field-error">{fieldErrors.email}</small>}
        </label>
        <label className="field">
          <span>Лозинка</span>
          <input
            type="password"
            autoComplete={registering ? 'new-password' : 'current-password'}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={Boolean(fieldErrors.password)}
          />
          {registering && !fieldErrors.password && <small className="muted">Најмалку 8 знаци.</small>}
          {fieldErrors.password && <small className="field-error">{fieldErrors.password}</small>}
        </label>
        {registering && (
          <label className="field">
            <span>Потврди ја лозинката</span>
            <input
              type="password"
              autoComplete="new-password"
              value={confirmation}
              onChange={(e) => setConfirmation(e.target.value)}
              aria-invalid={Boolean(fieldErrors.confirmation)}
            />
            {fieldErrors.confirmation && <small className="field-error">{fieldErrors.confirmation}</small>}
          </label>
        )}
        {!registering && (
          <p className="auth-forgot">
            <Link to="/forgot-password">Заборавена лозинка?</Link>
          </p>
        )}
        <button type="submit" className="button" disabled={submitting}>
          {registering ? 'Создај акаунт' : 'Најави се'}
        </button>
        <p className="muted auth-switch">
          {registering ? 'Веќе имаш акаунт? ' : 'Немаш акаунт? '}
          <Link to={registering ? '/login' : '/register'}>{registering ? 'Најави се' : 'Создај акаунт'}</Link>
        </p>
      </form>
    </AuthLayout>
  )
}
