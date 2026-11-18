import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, ApiError } from '../api'
import { useAuth } from '../auth'
import AuthLayout from '../components/AuthLayout'

export default function VerifyEmailPage() {
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const token = params.get('token')
  const [error, setError] = useState<string | null>(token ? null : 'Врската не е целосна. Отвори ја од е-поштата.')
  // A link works once, and development mode runs effects twice, so the request must go out only once.
  const sent = useRef(false)

  useEffect(() => {
    if (!token || sent.current) {
      return
    }
    sent.current = true
    api('/auth/verify-email', { method: 'POST', body: { token } })
      .then(() => signIn())
      .then(() => navigate('/', { replace: true }))
      .catch((caught) => setError(caught instanceof ApiError ? caught.message : 'Настана грешка. Обидете се повторно.'))
  }, [token, signIn, navigate])

  return (
    <AuthLayout>
      <div className="card auth-card">
        <h2>Потврда на е-пошта</h2>
        {error ? (
          <>
            <p role="alert" className="form-error">{error}</p>
            <p className="muted">Најави се со е-поштата и лозинката за да побараш нова врска.</p>
            <p className="muted auth-switch">
              <Link to="/login">Кон најава</Link>
            </p>
          </>
        ) : (
          <p className="muted">Се потврдува…</p>
        )}
      </div>
    </AuthLayout>
  )
}
