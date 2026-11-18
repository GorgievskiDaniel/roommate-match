import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../api'
import { useAuth } from '../auth'
import AuthLayout from '../components/AuthLayout'

export default function ResetPasswordPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const token = params.get('token')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState<string | null>(token ? null : 'Врската не е целосна. Отвори ја од е-поштата.')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    if (password !== confirmation) {
      setFieldErrors({ confirmation: 'Лозинките не се совпаѓаат.' })
      return
    }
    setSubmitting(true)
    try {
      await api('/auth/reset-password', {
        method: 'POST',
        body: { token, password },
      })
      await signIn()
      navigate('/', { replace: true })
    } catch (caught) {
      if (caught instanceof ApiError) {
        setError(caught.message)
        setFieldErrors(caught.fieldErrors)
      } else {
        setError('Настана грешка. Обидете се повторно.')
      }
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout>
      <form className="card auth-card" onSubmit={submit} noValidate>
        <h2>Нова лозинка</h2>
        {error && <p role="alert" className="form-error">{error}</p>}
        <label className="field">
          <span>Нова лозинка</span>
          <input
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={Boolean(fieldErrors.password)}
          />
          {!fieldErrors.password && <small className="muted">Најмалку 8 знаци.</small>}
          {fieldErrors.password && <small className="field-error">{fieldErrors.password}</small>}
        </label>
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
        <button type="submit" className="button" disabled={submitting || !token}>
          Зачувај ја лозинката
        </button>
        <p className="muted auth-switch">
          <Link to="/forgot-password">Побарај нова врска</Link>
        </p>
      </form>
    </AuthLayout>
  )
}
