import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, ApiError } from './api'
import type { CurrentUser } from './types'

interface AuthState {
  user: CurrentUser | null
  loading: boolean
  loadError: string | null
  /** Loads the account after the backend set the login cookie (login, confirmed e-mail, new password). */
  signIn: () => Promise<void>
  refresh: () => Promise<void>
  /** Ends the session on the server, on every device. */
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  // The cookie cannot be read from the page, so the backend is asked whether someone is logged in.
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const logout = useCallback(async () => {
    try {
      await api('/logout', { method: 'POST' })
    } catch {
      // The session already ended on the server (or it is unreachable); the page logs out anyway.
    }
    setUser(null)
  }, [])

  const refresh = useCallback(async () => {
    setLoadError(null)
    try {
      setUser(await api<CurrentUser>('/me'))
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setUser(null)
      } else {
        setLoadError(error instanceof Error ? error.message : 'Настана грешка.')
      }
    } finally {
      setLoading(false)
    }
  }, [])

  const signIn = refresh

  useEffect(() => {
    void refresh()
  }, [refresh])

  const value = useMemo(
    () => ({ user, loading, loadError, signIn, refresh, logout }),
    [user, loading, loadError, signIn, refresh, logout],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
