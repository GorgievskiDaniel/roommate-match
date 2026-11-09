import { useEffect, useRef, useState, type FormEvent } from 'react'
import { api, ApiError } from '../api'

interface Reply {
  found: boolean
  answer: string
  sources: string[]
}

interface Exchange {
  id: number
  question: string
  reply: Reply
}

const EXAMPLES = ['Кои домови има во Штип?', 'Кој дом е најблиску до ФИНКИ?', 'Колку е далеку ФЕИТ од Стив Наумов?']

export default function AssistantPage() {
  const [exchanges, setExchanges] = useState<Exchange[]>([])
  const [question, setQuestion] = useState('')
  const [pending, setPending] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const end = useRef<HTMLDivElement>(null)
  const nextId = useRef(1)

  useEffect(() => {
    end.current?.scrollIntoView?.({ block: 'end' })
  }, [exchanges, pending])

  async function ask(text: string) {
    const trimmed = text.trim()
    if (!trimmed || pending) return
    setPending(trimmed)
    setError(null)
    try {
      const reply = await api<Reply>('/assistant', { method: 'POST', body: { question: trimmed } })
      const id = nextId.current++
      setExchanges((current) => [...current, { id, question: trimmed, reply }])
      setQuestion('')
    } catch (caught) {
      if (caught instanceof ApiError) setError(caught.fieldErrors.question ?? caught.message)
      else setError('Настана грешка.')
    } finally {
      setPending(null)
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    void ask(question)
  }

  return (
    <div className="assistant">
      <h1>Асистент за домовите</h1>
      <p className="muted">Одговара од внесените документи за домовите, а растојанијата до факултети ги пресметува. За важни одлуки провери го и официјалниот извор.</p>

      <div className="assistant-log card" role="log" aria-live="polite">
        {exchanges.length === 0 && !pending && (
          <div className="assistant-empty">
            <p className="muted">На пример:</p>
            <div className="choices">
              {EXAMPLES.map((example) => (
                <button key={example} type="button" className="choice" onClick={() => void ask(example)}>{example}</button>
              ))}
            </div>
          </div>
        )}
        {exchanges.map((exchange) => (
          <div key={exchange.id} className="assistant-exchange">
            <div className="bubble mine"><p>{exchange.question}</p></div>
            <div className={exchange.reply.found ? 'bubble' : 'bubble not-found'}>
              <p>{exchange.reply.answer}</p>
              {exchange.reply.sources.length > 0 && <small className="muted">Извор: {exchange.reply.sources.join(', ')}</small>}
            </div>
          </div>
        ))}
        {pending && (
          <div className="assistant-exchange">
            <div className="bubble mine"><p>{pending}</p></div>
            <p className="muted">Се бара одговор…</p>
          </div>
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
