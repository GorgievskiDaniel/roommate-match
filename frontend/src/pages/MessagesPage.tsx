import { useCallback, useEffect, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../api'
import { useAuth } from '../auth'
import { useChat } from '../chat'
import type { ChatMessage, Conversation } from '../types'

const PAGE_SIZE = 50

/** Numeric on purpose: not every browser ships Macedonian month names. */
function formatTime(iso: string): string {
  const date = new Date(iso)
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}. ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** Keeps messages in order and without duplicates, since one can arrive by both REST and socket. */
function merge(current: ChatMessage[], incoming: ChatMessage[]): ChatMessage[] {
  const byId = new Map(current.map((message) => [message.id, message]))
  incoming.forEach((message) => byId.set(message.id, message))
  return [...byId.values()].sort((a, b) => a.id - b.id)
}

function Thread({ id, onChanged }: { id: number; onChanged: () => void }) {
  const { user } = useAuth()
  const { subscribe, connection, refreshUnread } = useChat()
  const navigate = useNavigate()
  const [conversation, setConversation] = useState<Conversation | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [hasOlder, setHasOlder] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const list = useRef<HTMLDivElement>(null)
  const scrollToEnd = useRef(true)

  const markRead = useCallback(() => {
    api<void>(`/conversations/${id}/read`, { method: 'POST' })
      .then(() => {
        refreshUnread()
        onChanged()
      })
      .catch(() => undefined)
  }, [id, refreshUnread, onChanged])

  // Runs again after a reconnection, to pick up what arrived in between.
  useEffect(() => {
    let active = true
    setError(null)
    Promise.all([
      api<Conversation>(`/conversations/${id}`),
      api<ChatMessage[]>(`/conversations/${id}/messages`),
    ])
      .then(([loaded, latest]) => {
        if (!active) return
        setConversation(loaded)
        setMessages((current) => merge(current.filter((m) => m.conversationId === id), latest))
        setHasOlder((older) => older || latest.length === PAGE_SIZE)
        scrollToEnd.current = true
        markRead()
      })
      .catch((caught: Error) => active && setError(caught.message))
    return () => {
      active = false
    }
  }, [id, connection, markRead])

  useEffect(() => subscribe((message) => {
    if (message.conversationId !== id) return
    scrollToEnd.current = true
    setMessages((current) => merge(current, [message]))
    if (message.senderId !== user?.id) markRead()
  }), [id, subscribe, markRead, user?.id])

  useLayoutEffect(() => {
    if (scrollToEnd.current && list.current) {
      list.current.scrollTop = list.current.scrollHeight
      scrollToEnd.current = false
    }
  }, [messages])

  async function loadOlder() {
    const older = await api<ChatMessage[]>(`/conversations/${id}/messages?before=${messages[0].id}`)
      .catch((caught: Error) => {
        setError(caught.message)
        return null
      })
    if (older) {
      setMessages((current) => merge(current, older))
      setHasOlder(older.length === PAGE_SIZE)
    }
  }

  async function send(event?: FormEvent) {
    event?.preventDefault()
    if (!draft.trim() || sending) return
    setSending(true)
    setError(null)
    try {
      const sent = await api<ChatMessage>(`/conversations/${id}/messages`, { method: 'POST', body: { body: draft } })
      scrollToEnd.current = true
      setMessages((current) => merge(current, [sent]))
      setDraft('')
      onChanged()
    } catch (caught) {
      if (caught instanceof ApiError) setError(caught.fieldErrors.body ?? caught.message)
      else setError('Настана грешка.')
    } finally {
      setSending(false)
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      void send()
    }
  }

  async function block() {
    if (!conversation) return
    if (!window.confirm(`Да го блокирам корисникот ${conversation.otherName}? Разговорот ќе се скрие и нема да можете да си пишувате.`)) return
    try {
      await api<void>(`/blocks/${conversation.otherUserId}`, { method: 'POST' })
      onChanged()
      refreshUnread()
      navigate('/messages')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Настана грешка.')
    }
  }

  if (error && !conversation) return <p role="alert" className="form-error">{error}</p>
  if (!conversation) return <p className="muted">Се вчитува…</p>

  return (
    <section className="thread card">
      <header className="thread-head">
        <Link to="/messages" className="thread-back">← Назад</Link>
        <h2>{conversation.otherName}</h2>
        <button type="button" className="link-button" onClick={() => void block()}>Блокирај</button>
      </header>
      <div className="thread-messages" ref={list} role="log" aria-live="polite">
        {hasOlder && (
          <button type="button" className="link-button older" onClick={() => void loadOlder()}>Вчитај постари</button>
        )}
        {messages.length === 0 && <p className="muted">Сè уште нема пораки. Напиши ја првата.</p>}
        {messages.map((message) => (
          <div key={message.id} className={message.senderId === user?.id ? 'bubble mine' : 'bubble'}>
            <p>{message.body}</p>
            <time dateTime={message.createdAt}>{formatTime(message.createdAt)}</time>
          </div>
        ))}
      </div>
      {error && <p role="alert" className="form-error">{error}</p>}
      <form className="composer" onSubmit={(event) => void send(event)}>
        <label className="sr-only" htmlFor="message-body">Порака</label>
        <textarea
          id="message-body"
          rows={2}
          maxLength={2000}
          placeholder="Напиши порака…"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={onKeyDown}
        />
        <button type="submit" className="button" disabled={sending || !draft.trim()}>Испрати</button>
      </form>
    </section>
  )
}

function BlockedUsers({ version, onChanged }: { version: number; onChanged: () => void }) {
  const [blocked, setBlocked] = useState<{ userId: number; firstName: string }[]>([])

  useEffect(() => {
    let active = true
    api<{ userId: number; firstName: string }[]>('/blocks')
      .then((loaded) => active && setBlocked(loaded))
      .catch(() => undefined)
    return () => {
      active = false
    }
  }, [version])

  if (blocked.length === 0) return null
  return (
    <section className="blocked">
      <h3>Блокирани</h3>
      {blocked.map((person) => (
        <div key={person.userId} className="blocked-row">
          <span>{person.firstName}</span>
          <button type="button" className="link-button" onClick={() => {
            api<void>(`/blocks/${person.userId}`, { method: 'DELETE' }).then(onChanged).catch(() => undefined)
          }}>
            Одблокирај
          </button>
        </div>
      ))}
    </section>
  )
}

export default function MessagesPage() {
  const params = useParams()
  const openId = params.id ? Number(params.id) : null
  const { subscribe, connection, refreshUnread } = useChat()
  const [conversations, setConversations] = useState<Conversation[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [version, setVersion] = useState(0)
  const changed = useCallback(() => setVersion((current) => current + 1), [])

  useEffect(() => {
    let active = true
    api<Conversation[]>('/conversations')
      .then((loaded) => active && setConversations(loaded))
      .catch((caught: Error) => active && setError(caught.message))
    refreshUnread()
    return () => {
      active = false
    }
  }, [version, connection, refreshUnread])

  useEffect(() => subscribe(changed), [subscribe, changed])

  return (
    <div className={openId ? 'messages-page thread-open' : 'messages-page'}>
      <aside className="conversation-list card">
        <h1>Пораки</h1>
        {error && <p role="alert" className="form-error">{error}</p>}
        {conversations?.length === 0 && (
          <p className="muted">Сè уште немаш разговори. Напиши порака од <Link to="/matches">совпаѓањата</Link> или од <Link to="/">мапата</Link>.</p>
        )}
        {conversations?.map((conversation) => (
          <Link
            key={conversation.id}
            to={`/messages/${conversation.id}`}
            className={conversation.id === openId ? 'conversation selected' : 'conversation'}
          >
            <span className="conversation-name">
              {conversation.otherName}
              {conversation.unread > 0 && <span className="badge" aria-label={`${conversation.unread} непрочитани`}>{conversation.unread}</span>}
            </span>
            <span className="conversation-last">{conversation.lastMessage}</span>
          </Link>
        ))}
        <BlockedUsers version={version} onChanged={changed} />
      </aside>
      {openId
        ? <Thread key={openId} id={openId} onChanged={changed} />
        : <p className="muted thread-empty">Избери разговор.</p>}
    </div>
  )
}
