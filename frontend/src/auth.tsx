import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, ApiError, getToken, setToken } from './api'
import type { CurrentUser } from './types'

interface AuthState {
  user: CurrentUser | null
  loading: boolean
  loadError: string | null
  authenticate: (path: 'login' | 'register', email: string, password: string) => Promise<void>
  refresh: () => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(getToken() !== null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const logout = useCallback(() => {
    setToken(null)
    setUser(null)
  }, [])

  const refresh = useCallback(async () => {
    setLoadError(null)
    try {
      setUser(await api<CurrentUser>('/me'))
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        logout()
      } else {
        setLoadError(error instanceof Error ? error.message : 'Настана грешка.')
      }
    } finally {
      setLoading(false)
    }
  }, [logout])

  const authenticate = useCallback(
    async (path: 'login' | 'register', email: string, password: string) => {
      const { token } = await api<{ token: string }>(`/auth/${path}`, {
        method: 'POST',
        body: { email, password },
      })
      setToken(token)
      await refresh()
    },
    [refresh],
  )

  useEffect(() => {
    if (getToken()) {
      void refresh()
    }
  }, [refresh])

  const value = useMemo(
    () => ({ user, loading, loadError, authenticate, refresh, logout }),
    [user, loading, loadError, authenticate, refresh, logout],
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
