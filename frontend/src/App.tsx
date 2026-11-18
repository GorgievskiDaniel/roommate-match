import type { ReactNode } from 'react'
import { Link, Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './auth'
import { useChat } from './chat'
import AssistantPage from './pages/AssistantPage'
import AuthPage from './pages/AuthPage'
import BrowsePage from './pages/BrowsePage'
import DormDetailPage from './pages/DormDetailPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ListingDetailPage from './pages/ListingDetailPage'
import ListingPage from './pages/ListingPage'
import MapPage from './pages/MapPage'
import MatchesPage from './pages/MatchesPage'
import MessagesPage from './pages/MessagesPage'
import ProfileFormPage from './pages/ProfileFormPage'
import ProfilePage from './pages/ProfilePage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import VerifyEmailPage from './pages/VerifyEmailPage'

function Shell({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const { unread } = useChat()
  const onRegister = useLocation().pathname === '/register'
  return (
    <>
      <header className="topbar">
        <Link to="/" className="brand">
          Roommate<span>Match</span>
        </Link>
        {user && (
          <nav className="nav">
            {user.profileCompleted && (
              <>
                <NavLink to="/" end>Мапа</NavLink>
                <NavLink to="/listings">Огласи</NavLink>
                <NavLink to="/matches">Совпаѓања</NavLink>
                <NavLink to="/messages">
                  Пораки
                  {unread > 0 && <span className="badge" aria-label={`${unread} непрочитани`}>{unread}</span>}
                </NavLink>
                <NavLink to="/assistant">Асистент</NavLink>
                <NavLink to="/listing">Мој оглас</NavLink>
                <NavLink to="/profile">Профил</NavLink>
              </>
            )}
            <button type="button" className="link-button" onClick={() => void logout()}>
              Одјави се
            </button>
          </nav>
        )}
        {!user && (
          <Link to={onRegister ? '/login' : '/register'} className="topbar-cta">
            {onRegister ? 'Најави се' : 'Создај акаунт'}
          </Link>
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
        <Route path="/login" element={<Guard need="guest"><AuthPage key="login" mode="login" /></Guard>} />
        <Route path="/register" element={<Guard need="guest"><AuthPage key="register" mode="register" /></Guard>} />
        <Route path="/forgot-password" element={<Guard need="guest"><ForgotPasswordPage /></Guard>} />
        {/* Links from e-mails work whether or not someone is logged in on this browser. */}
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/onboarding" element={<Guard need="onboarding"><ProfileFormPage /></Guard>} />
        <Route path="/profile/edit" element={<Guard need="member"><ProfileFormPage editing /></Guard>} />
        <Route path="/profile" element={<Guard need="member"><ProfilePage /></Guard>} />
        <Route path="/matches" element={<Guard need="member"><MatchesPage /></Guard>} />
        <Route path="/assistant" element={<Guard need="member"><AssistantPage /></Guard>} />
        <Route path="/messages" element={<Guard need="member"><MessagesPage /></Guard>} />
        <Route path="/messages/:id" element={<Guard need="member"><MessagesPage /></Guard>} />
        <Route path="/listings" element={<Guard need="member"><BrowsePage /></Guard>} />
        <Route path="/listings/:id" element={<Guard need="member"><ListingDetailPage /></Guard>} />
        <Route path="/dorms/:id" element={<Guard need="member"><DormDetailPage /></Guard>} />
        <Route path="/listing" element={<Guard need="member"><ListingPage /></Guard>} />
        <Route path="/" element={<Guard need="member"><MapPage /></Guard>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Shell>
  )
}
