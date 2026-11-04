import type { ReactNode } from 'react'
import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth'
import AuthPage from './pages/AuthPage'
import ProfileFormPage from './pages/ProfileFormPage'
import ProfilePage from './pages/ProfilePage'

function Shell({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  return (
    <>
      <header className="topbar">
        <Link to="/" className="brand">
          Roommate<span>Match</span>
        </Link>
        {user && (
          <button type="button" className="link-button" onClick={logout}>
            Одјави се
          </button>
        )}
      </header>
      <main className="page">{children}</main>
    </>
  )
}

/** Sends each visitor to the one screen that fits where they are in sign-up. */
function Guard({ need, children }: { need: 'guest' | 'onboarding' | 'member' | 'user'; children: ReactNode }) {
  const { user } = useAuth()
  if (!user) {
    return need === 'guest' ? children : <Navigate to="/login" replace />
  }
  if (need === 'guest') {
    return <Navigate to="/" replace />
  }
  if (need === 'member' && !user.profileCompleted) {
    return <Navigate to="/onboarding" replace />
  }
  if (need === 'onboarding' && user.profileCompleted) {
    return <Navigate to="/" replace />
  }
  return children
}

export default function App() {
  const { loading, loadError, refresh } = useAuth()

  if (loading) {
    return (
      <Shell>
        <p className="muted">Се вчитува…</p>
      </Shell>
    )
  }
  if (loadError) {
    return (
      <Shell>
        <p role="alert" className="form-error">{loadError}</p>
        <button type="button" className="button" onClick={() => void refresh()}>
          Обиди се повторно
        </button>
      </Shell>
    )
  }

  return (
    <Shell>
      <Routes>
        <Route path="/login" element={<Guard need="guest"><AuthPage mode="login" /></Guard>} />
        <Route path="/register" element={<Guard need="guest"><AuthPage mode="register" /></Guard>} />
        <Route path="/onboarding" element={<Guard need="onboarding"><ProfileFormPage /></Guard>} />
        <Route path="/profile/edit" element={<Guard need="member"><ProfileFormPage editing /></Guard>} />
        <Route path="/" element={<Guard need="member"><ProfilePage /></Guard>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Shell>
  )
}
