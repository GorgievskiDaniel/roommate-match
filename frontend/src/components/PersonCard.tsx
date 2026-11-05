import { LABELS, type ProfileSummary } from '../types'

export default function PersonCard({ person, note }: { person: ProfileSummary; note?: string | null }) {
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
    </article>
  )
}
