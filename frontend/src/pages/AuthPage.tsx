import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api'
import { useAuth } from '../auth'

export default function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { authenticate } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const registering = mode === 'register'

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      await authenticate(mode, email, password)
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
    <div className="auth-layout">
      <section className="auth-intro">
        <h1>Најди цимер со кој навистина можеш да живееш.</h1>
        <p>Одговори на неколку прашања и види кој од студентите ти одговара за стан, куќа или соба во дом.</p>
      </section>
      <form className="card auth-card" onSubmit={submit} noValidate>
        <h2>{registering ? 'Создај акаунт' : 'Најави се'}</h2>
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
        <button type="submit" className="button" disabled={submitting}>
          {registering ? 'Создај акаунт' : 'Најави се'}
        </button>
        <p className="muted auth-switch">
          {registering ? 'Веќе имаш акаунт? ' : 'Немаш акаунт? '}
          <Link to={registering ? '/login' : '/register'}>{registering ? 'Најави се' : 'Создај акаунт'}</Link>
        </p>
      </form>
    </div>
  )
}
