import { Client } from '@stomp/stompjs'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { api, getToken } from './api'
import { useAuth } from './auth'
import type { ChatMessage } from './types'

type Listener = (message: ChatMessage) => void

interface ChatState {
  unread: number
  refreshUnread: () => void
  /** Grows with every (re)connection, so screens can catch up on what they missed while offline. */
  connection: number
  subscribe: (listener: Listener) => () => void
}

const ChatContext = createContext<ChatState | null>(null)

export function ChatProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const userId = user?.id ?? null
  const profileCompleted = user?.profileCompleted ?? false
  const [unread, setUnread] = useState(0)
  const [connection, setConnection] = useState(0)
  const listeners = useRef(new Set<Listener>())

  const refreshUnread = useCallback(() => {
    api<{ unread: number }>('/conversations/unread')
      .then((result) => setUnread(result.unread))
      .catch(() => undefined)
  }, [])

  useEffect(() => {
    if (userId === null || !profileCompleted) {
      setUnread(0)
      return
    }
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${scheme}://${window.location.host}/ws`,
      reconnectDelay: 3000,
      // Runs before each attempt, so a reconnection uses the current token.
      beforeConnect: async (stomp) => {
        stomp.connectHeaders = { Authorization: `Bearer ${getToken()}` }
      },
      onConnect: () => {
        client.subscribe('/user/queue/messages', (frame) => {
          const message = JSON.parse(frame.body) as ChatMessage
          listeners.current.forEach((listener) => listener(message))
          refreshUnread()
        })
        setConnection((current) => current + 1)
        refreshUnread()
      },
    })
    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [userId, profileCompleted, refreshUnread])

  const subscribe = useCallback((listener: Listener) => {
    listeners.current.add(listener)
    return () => {
      listeners.current.delete(listener)
    }
  }, [])

  const value = useMemo(
    () => ({ unread, refreshUnread, connection, subscribe }),
    [unread, refreshUnread, connection, subscribe],
  )
  return <ChatContext.Provider value={value}>{children}</ChatContext.Provider>
}

export function useChat(): ChatState {
  const context = useContext(ChatContext)
  if (!context) {
    throw new Error('useChat must be used inside ChatProvider')
  }
  return context
}
