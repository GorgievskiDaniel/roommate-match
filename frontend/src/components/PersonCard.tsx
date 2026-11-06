import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import { LABELS, type Conversation, type ProfileSummary } from '../types'

function MessageButton({ userId }: { userId: number }) {
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
      <button type="button" className="button secondary" disabled={opening} onClick={() => void open()}>
        Напиши порака
      </button>
      {error && <small role="alert" className="field-error">{error}</small>}
    </div>
  )
}

export default function PersonCard({ person, note }: { person: ProfileSummary; note?: string | null }) {
  const { user } = useAuth()
  return (
    <article className="person">
      <h4>{person.firstName}, {person.age}</h4>
      <p className="muted">{person.faculty}{note ? ` · ${note}` : ''}</p>
      {person.bio && <p className="person-bio">{person.bio}</p>}
      <ul className="facts">
        <li>{person.smoker ? 'Пушач' : 'Непушач'}</li>
        <li>{person.hasPets ? 'Има миленичиња' : 'Без миленичиња'}</li>
        <li>Гости: {LABELS.guestsFrequency[person.guestsFrequency].toLowerCase()}</li>
        <li>Уреденост: {person.tidiness}/5</li>
        <li>{LABELS.sleepSchedule[person.sleepSchedule]}</li>
        <li>Тишина: {person.quietNeed}/5</li>
      </ul>
      <ul className="tags">
        {person.hobbies.map((hobby) => <li key={hobby}>{hobby}</li>)}
      </ul>
      {user && user.id !== person.userId && <MessageButton userId={person.userId} />}
    </article>
  )
}
