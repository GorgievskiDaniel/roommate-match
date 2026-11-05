import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { LABELS, wantedPlace, type Profile, type Reference } from '../types'

const yesNo = (value: boolean) => (value ? 'Да' : 'Не')

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="row">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}

export default function ProfilePage() {
  const [data, setData] = useState<{ profile: Profile; reference: Reference } | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    Promise.all([api<Profile>('/profile/me'), api<Reference>('/reference')])
      .then(([profile, reference]) => active && setData({ profile, reference }))
      .catch((caught: Error) => active && setError(caught.message))
    return () => {
      active = false
    }
  }, [])

  if (error) return <p role="alert" className="form-error">{error}</p>
  if (!data) return <p className="muted">Се вчитува…</p>

  const { profile: p, reference: r } = data
  const nameOf = (options: { id: number; name: string }[], id: number) =>
    options.find((option) => option.id === id)?.name ?? ''

  return (
    <div className="profile">
      <div className="profile-head">
        <div>
          <p className="eyebrow">Твојот профил</p>
          <h1>{p.firstName}, {p.age}</h1>
          <p className="muted">{p.faculty}</p>
        </div>
        <Link to="/profile/edit" className="button secondary">Измени</Link>
      </div>
      {p.bio && <p className="bio">{p.bio}</p>}

      <div className="profile-grid">
        <section className="card">
          <h2>Што бара</h2>
          <dl>
            <Row label="Каде" value={LABELS.wantedPlace[wantedPlace(p.housingType, p.propertyTypes) ?? 'BOTH']} />
            {p.housingType === 'DORM' && p.dormId !== null && <Row label="Дом" value={nameOf(r.dorms, p.dormId)} />}
            {p.dormRoomOptionId !== null && (
              <Row
                label="Соба"
                value={nameOf(r.dorms.flatMap((d) => d.roomOptions), p.dormRoomOptionId)}
              />
            )}
            {p.housingType === 'APARTMENT' && p.situation && (
              <>
                <Row label="Положба" value={LABELS.situation[p.situation]} />
                <Row label="Кирија" value={`${p.budgetMin}–${p.budgetMax} €`} />
                <Row label="Локации" value={r.areas.filter((a) => p.areas.includes(a.code)).map((a) => a.name).join(', ')} />
              </>
            )}
          </dl>
        </section>
        <section className="card">
          <h2>Навики</h2>
          <dl>
            <Row label="Пушач" value={yesNo(p.smoker)} />
            <Row label="Миленичиња" value={yesNo(p.hasPets)} />
            <Row label="Гости" value={LABELS.guestsFrequency[p.guestsFrequency]} />
            <Row label="Уреденост" value={`${p.tidiness} од 5`} />
            <Row label="Спиење" value={LABELS.sleepSchedule[p.sleepSchedule]} />
            <Row label="Потреба за тишина" value={`${p.quietNeed} од 5`} />
          </dl>
        </section>
        <section className="card">
          <h2>Цимер</h2>
          <dl>
            <Row label="Цимер пушач" value={yesNo(p.acceptsSmoker)} />
            <Row label="Цимер со миленичиња" value={yesNo(p.acceptsPets)} />
            <Row label="Пол на цимерот" value={LABELS.genderPreference[p.roommateGenderPreference]} />
          </dl>
          <h2 className="subhead">Хобиња</h2>
          <ul className="tags">
            {p.hobbyIds.map((id) => <li key={id}>{nameOf(r.hobbies, id)}</li>)}
          </ul>
        </section>
      </div>
    </div>
  )
}
