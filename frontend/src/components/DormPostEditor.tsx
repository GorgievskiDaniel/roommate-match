import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api'
import type { DormOption } from '../types'

interface DormPost {
  text: string
  dormId: number
  dorm: string
  updatedAt: string
}

const LIMIT = 1000

/** A short ad for a roommate in a dorm, shown on that dorm's page. Anyone may post one, for any dorm. */
export default function DormPostEditor({ dorms, defaultDormId }: { dorms: DormOption[]; defaultDormId: number | null }) {
  const [post, setPost] = useState<DormPost | null>(null)
  const [dormId, setDormId] = useState<number | null>(defaultDormId)
  const [loaded, setLoaded] = useState(false)
  const [text, setText] = useState('')
  const [editing, setEditing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    let active = true
    api<DormPost>('/dorm-posts/mine')
      .then((found) => {
        if (!active) return
        setPost(found)
        setText(found.text)
        setDormId(found.dormId)
      })
      .catch((caught) => {
        if (active && !(caught instanceof ApiError && caught.status === 404)) setError(caught.message)
      })
      .finally(() => active && setLoaded(true))
    return () => {
      active = false
    }
  }, [])

  async function save(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      const saved = await api<DormPost>('/dorm-posts/mine', { method: 'PUT', body: { dormId, text } })
      setPost(saved)
      setEditing(false)
    } catch (caught) {
      if (caught instanceof ApiError) setError(caught.fieldErrors.text ?? caught.fieldErrors.dormId ?? caught.message)
      else setError('Настана грешка.')
    } finally {
      setSaving(false)
    }
  }

  async function remove() {
    setError(null)
    try {
      await api<void>('/dorm-posts/mine', { method: 'DELETE' })
      setPost(null)
      setText('')
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Настана грешка.')
    }
  }

  if (!loaded) return <p className="muted">Се вчитува…</p>

  if (post && !editing) {
    return (
      <div className="card narrow dorm-post">
        <p className="eyebrow">Твојот оглас · {post.dorm}</p>
        <h1>Бараш цимер во дом</h1>
        <blockquote>{post.text}</blockquote>
        {error && <p role="alert" className="form-error">{error}</p>}
        <div className="actions">
          <button type="button" className="button secondary" onClick={() => setEditing(true)}>Измени</button>
          <button type="button" className="button danger" onClick={() => void remove()}>Избриши</button>
          <Link to={`/dorms/${post.dormId}`}>Види го на страницата на домот</Link>
        </div>
      </div>
    )
  }

  return (
    <form className="card narrow dorm-post" onSubmit={save} noValidate>
      <h1>{post ? 'Измени го огласот' : 'Оглас за цимер во дом'}</h1>
      <p className="muted">
        Огласот се прикажува на страницата на домот, заедно со твојот профил, за да ти пишат студентите
        што бараат цимер таму.
      </p>
      {error && <p role="alert" className="form-error">{error}</p>}
      <label className="field">
        <span>Студентски дом</span>
        <select value={dormId ?? ''} onChange={(event) => setDormId(event.target.value ? Number(event.target.value) : null)}>
          <option value="">Избери дом</option>
          {dorms.map((dorm) => (
            <option key={dorm.id} value={dorm.id}>{dorm.openingNote ? `${dorm.name} (уште не е отворен)` : dorm.name}</option>
          ))}
        </select>
      </label>
      <label className="field">
        <span>Што бараш</span>
        <textarea
          rows={6}
          maxLength={LIMIT}
          value={text}
          placeholder="На пример: Барам цимерка за соба во блок А од септември. Учам доцна, но сум тивка и уредна."
          onChange={(event) => setText(event.target.value)}
        />
        <small className="muted">{text.length}/{LIMIT}</small>
      </label>
      <div className="actions">
        <button type="submit" className="button" disabled={saving || !text.trim() || dormId === null}>
          {post ? 'Зачувај' : 'Објави го огласот'}
        </button>
        {post && <button type="button" className="button secondary" onClick={() => { setText(post.text); setEditing(false) }}>Откажи</button>}
      </div>
    </form>
  )
}
