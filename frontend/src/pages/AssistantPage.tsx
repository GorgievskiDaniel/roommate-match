import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api, ApiError } from '../api'

interface Attachment {
  name: string
  title: string
}

interface WebLink {
  title: string
  url: string
}

interface Message {
  role: 'USER' | 'ASSISTANT'
  content: string
  sources: string[]
  attachments: Attachment[]
  /** Not empty only when the answer was found on the web rather than in the documents. */
  webLinks: WebLink[]
  /** Google's suggestions, shown with a fresh answer from the web as its terms require. */
  searchSuggestions?: string | null
}

interface Reply {
  found: boolean
  answer: string
  sources: string[]
  attachments: Attachment[]
  webLinks: WebLink[]
  searchSuggestions: string | null
}

const EXAMPLES = ['Кои документи ми се потребни за конкурсот?', 'Кој дом е најблиску до ФИНКИ?', 'Кои домови има во Штип?']

/** The file is fetched first, so a failed download shows an error instead of an error page. */
async function download(attachment: Attachment) {
  const response = await fetch(`/api/assistant/files?name=${encodeURIComponent(attachment.name)}`)
  if (!response.ok) throw new Error('Документот не може да се преземе.')
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = attachment.name
  link.click()
  URL.revokeObjectURL(url)
}

export default function AssistantPage() {
  const [messages, setMessages] = useState<Message[] | null>(null)
  const [question, setQuestion] = useState('')
  const [pending, setPending] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const end = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let active = true
    api<Message[]>('/assistant/history')
      .then((loaded) => active && setMessages(loaded))
      .catch((caught: Error) => {
        if (!active) return
        setMessages([])
        setError(caught.message)
      })
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    end.current?.scrollIntoView?.({ block: 'end' })
  }, [messages, pending])

  async function ask(text: string) {
    const trimmed = text.trim()
    if (!trimmed || pending) return
    setPending(trimmed)
    setError(null)
    setQuestion('')
    try {
      const reply = await api<Reply>('/assistant', { method: 'POST', body: { question: trimmed } })
      setMessages((current) => [
        ...(current ?? []),
        { role: 'USER', content: trimmed, sources: [], attachments: [], webLinks: [] },
        {
          role: 'ASSISTANT',
          content: reply.answer,
          sources: reply.sources,
          attachments: reply.attachments,
          webLinks: reply.webLinks,
          searchSuggestions: reply.searchSuggestions,
        },
      ])
    } catch (caught) {
      // The question comes back into the field, so it can be sent again without retyping.
      setQuestion(trimmed)
      if (caught instanceof ApiError) setError(caught.fieldErrors.question ?? caught.message)
      else setError('Настана грешка.')
    } finally {
      setPending(null)
    }
  }

  async function startOver() {
    try {
      await api<void>('/assistant/history', { method: 'DELETE' })
      setMessages([])
      setError(null)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Настана грешка.')
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    void ask(question)
  }

  return (
    <div className="assistant">
      <div className="assistant-head">
        <h1>Асистент за домовите</h1>
        {messages && messages.length > 0 && (
          <button type="button" className="button secondary" onClick={() => void startOver()}>Нов разговор</button>
        )}
      </div>
      <p className="muted">Одговара од внесените документи за домовите, а растојанијата до факултети ги пресметува. Што нема во документите, го бара на интернет. За важни одлуки провери го и официјалниот извор.</p>

      <div className="assistant-log card" role="log" aria-live="polite">
        {messages === null && <p className="muted">Се вчитува…</p>}
        {messages?.length === 0 && !pending && (
          <div className="assistant-empty">
            <p className="muted">На пример:</p>
            <div className="choices">
              {EXAMPLES.map((example) => (
                <button key={example} type="button" className="choice" onClick={() => void ask(example)}>{example}</button>
              ))}
            </div>
          </div>
        )}
        {messages?.map((message, index) => (
          <div key={index} className={message.role === 'USER' ? 'bubble mine' : 'bubble'}>
            <p>{message.content}</p>
            {message.sources.length > 0 && <small className="muted">Извор: {message.sources.join(', ')}</small>}
            {message.webLinks?.length > 0 && (
              <div className="web-answer">
                <small className="web-label">Од интернет, не од документите. Провери ги изворите.</small>
                <ul className="attachments">
                  {message.webLinks.map((link) => (
                    <li key={link.url}>
                      <a href={link.url} target="_blank" rel="noopener noreferrer">{link.title}</a>
                    </li>
                  ))}
                </ul>
                {message.searchSuggestions && (
                  // Google's own markup, kept apart from the page: no scripts, and links open in a new tab.
                  <iframe
                    className="search-suggestions"
                    title="Пребарувања на Google"
                    sandbox="allow-popups allow-popups-to-escape-sandbox"
                    srcDoc={`<base target="_blank">${message.searchSuggestions}`}
                  />
                )}
              </div>
            )}
            {message.attachments.length > 0 && (
              <ul className="attachments">
                {message.attachments.map((attachment) => (
                  <li key={attachment.name}>
                    <button type="button" className="link-button" onClick={() => {
                      download(attachment).catch((caught: Error) => setError(caught.message))
                    }}>
                      Преземи: {attachment.title}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        ))}
        {pending && (
          <>
            <div className="bubble mine"><p>{pending}</p></div>
            <p className="muted">Се бара одговор…</p>
          </>
        )}
        <div ref={end} />
      </div>

      {error && <p role="alert" className="form-error">{error}</p>}
      <form className="map-search" onSubmit={submit}>
        <label className="sr-only" htmlFor="assistant-question">Прашање</label>
        <input
          id="assistant-question"
          value={question}
          maxLength={500}
          placeholder="Прашај нешто за студентските домови"
          onChange={(event) => setQuestion(event.target.value)}
        />
        <button type="submit" className="button" disabled={Boolean(pending) || !question.trim()}>Прашај</button>
      </form>
    </div>
  )
}
