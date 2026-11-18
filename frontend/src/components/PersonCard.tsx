import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import Avatar from './Avatar'
import { LABELS, SHORT, type Conversation, type ProfileSummary } from '../types'

/** Opens (or continues) the conversation with one person. */
export function MessageButton({ userId, label = 'Напиши порака', primary = false }: { userId: number; label?: string; primary?: boolean }) {
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [opening, setOpening] = useState(false)

  async function open() {
    setOpening(true)
    setError(null)
    try {
      const conversation = await api<Conversation>('/conversations', { method: 'POST', body: { userId } })
      navigate(`/messages/${conversation.id}`)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Настана грешка.')
      setOpening(false)
    }
  }

  return (
    <div className="message-action">
      <button type="button" className={primary ? 'button' : 'button secondary'} disabled={opening} onClick={() => void open()}>
        {label}
      </button>
      {error && <small role="alert" className="field-error">{error}</small>}
    </div>
  )
}

export default function PersonCard({ person, note, post }: { person: ProfileSummary; note?: string | null; post?: string | null }) {
  const { user } = useAuth()
  return (
    <article className="person">
      <div className="person-head">
        <Avatar url={person.avatarUrl} gender={person.gender} name={person.firstName} />
        <div>
          <h4>{person.firstName}, {person.age}</h4>
          <p className="muted">{person.faculty}{note ? ` · ${note}` : ''}</p>
        </div>
      </div>
      {post && <blockquote className="person-post"><span className="eyebrow">Оглас</span>{post}</blockquote>}
      {person.bio && <p className="person-bio">{person.bio}</p>}
      <ul className="facts">
        {person.smoker !== null && <li>{person.smoker ? 'Пушач' : 'Непушач'}</li>}
        {person.hasPets !== null && <li>{person.hasPets ? 'Има миленичиња' : 'Без миленичиња'}</li>}
        <li>Гости: {LABELS.guestsFrequency[person.guestsFrequency].toLowerCase()}</li>
        <li>Чистење: {SHORT.cleaning[person.cleaning]}</li>
        <li>Спиење: {LABELS.sleepSchedule[person.sleepSchedule].toLowerCase()}</li>
        <li>Музика: {SHORT.sound[person.sound]}</li>
        <li>Комуникација: {SHORT.communication[person.communication]}</li>
        <li>Делење: {SHORT.sharing[person.sharing]}</li>
        <li>Забави: {SHORT.parties[person.parties]}</li>
        <li>Партнер преноќува: {SHORT.partnerStays[person.partnerStays]}</li>
      </ul>
      <ul className="tags">
        {person.hobbies.map((hobby) => <li key={hobby}>{hobby}</li>)}
      </ul>
      {user && user.id !== person.userId && <MessageButton userId={person.userId} />}
    </article>
  )
}
