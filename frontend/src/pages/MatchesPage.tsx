import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import PersonCard from '../components/PersonCard'
import { LABELS, type Match } from '../types'

function Part({ label, value }: { label: string; value: number }) {
  return (
    <div className="part">
      <div className="part-label">
        <span>{label}</span>
        <span>{value}%</span>
      </div>
      <div className="part-track" aria-hidden="true">
        <span style={{ width: `${value}%` }} />
      </div>
    </div>
  )
}

function MatchCard({ match }: { match: Match }) {
  const { profile, score } = match
  return (
    <article className="card match">
      <div className="match-head">
        <div className="match-total">
          <strong>{score.total}%</strong>
          <span>совпаѓање</span>
        </div>
        <div className="match-parts">
          <Part label="Навики" value={score.habits} />
          {score.rent !== null && <Part label="Кирија" value={score.rent} />}
          {score.location !== null && <Part label="Локација" value={score.location} />}
          <Part label="Хобиња" value={score.hobbies} />
        </div>
      </div>
      {score.differentRoomType && (
        <p className="notice">Бара различен тип на соба, па процентот е намален наполовина.</p>
      )}
      <PersonCard person={profile} note={match.roomOption} />
      {match.situation && <p className="muted">{LABELS.situation[match.situation]}</p>}
      {match.areas.length > 0 && <p className="muted">Локација: {match.areas.join(', ')}</p>}
      {match.listingId !== null && (
        <Link to={`/?listing=${match.listingId}`} className="button secondary match-link">Види го станот на мапа</Link>
      )}
    </article>
  )
}

export default function MatchesPage() {
  const [matches, setMatches] = useState<Match[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    api<Match[]>('/matches')
      .then((loaded) => active && setMatches(loaded))
      .catch((caught: Error) => active && setError(caught.message))
    return () => {
      active = false
    }
  }, [])

  if (error) return <p role="alert" className="form-error">{error}</p>
  if (!matches) return <p className="muted">Се вчитува…</p>

  return (
    <div className="matches">
      <h1>Твоите совпаѓања</h1>
      <p className="muted">Подредени од најдобро. Се прикажуваат само студенти со кои условите одговараат и за двајцата.</p>
      {matches.length === 0 && (
        <div className="card">
          <h2>Сè уште нема совпаѓања</h2>
          <p className="muted">Кога ќе се пријават студенти што ти одговараат, ќе се појават тука. Можеш да ги прошириш локациите или условите во профилот.</p>
          <Link to="/profile/edit" className="button secondary match-link">Измени го профилот</Link>
        </div>
      )}
      {matches.map((match) => <MatchCard key={match.profile.userId} match={match} />)}
    </div>
  )
}
