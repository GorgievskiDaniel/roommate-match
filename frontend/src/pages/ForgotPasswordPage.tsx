import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api'
import AuthLayout from '../components/AuthLayout'

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [sentTo, setSentTo] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      await api('/auth/forgot-password', { method: 'POST', body: { email } })
      setSentTo(email.trim())
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message)
        setFieldErrors(caught.fieldErrors)
      } else {
        setError('Настана грешка. Обидете се повторно.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      {sentTo ? (
        <div className="card auth-card">
          <h2>Провери ја е-поштата</h2>
          {/* The backend never says whether the address has an account, so neither does this page. */}
          <p>
            Ако постои акаунт со <strong>{sentTo}</strong>, ти испративме врска за нова лозинка. Врската важи 1 час.
          </p>
          <p className="muted">Ако не ја гледаш, провери ја папката за спам.</p>
          <p className="muted auth-switch">
            <Link to="/login">Назад кон најава</Link>
          </p>
        </div>
      ) : (
        <form className="card auth-card" onSubmit={submit} noValidate>
          <h2>Заборавена лозинка</h2>
          <p className="muted">Внеси ја е-поштата на акаунтот и ќе ти испратиме врска за нова лозинка.</p>
          {error && <p role="alert" className="form-error">{error}</p>}
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
          <button type="submit" className="button" disabled={submitting}>
            Испрати врска
          </button>
          <p className="muted auth-switch">
            <Link to="/login">Назад кон најава</Link>
          </p>
        </form>
      )}
    </AuthLayout>
  )
}
