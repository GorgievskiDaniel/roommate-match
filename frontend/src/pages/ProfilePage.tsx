import { useEffect, useState, type ChangeEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api'
import Avatar from '../components/Avatar'
import DeleteAccount from '../components/DeleteAccount'
import { LABELS, wantedPlace, type Profile, type Reference } from '../types'

const yesNo = (value: boolean) => (value ? 'Да' : 'Не')

/** The picture with buttons to change or remove it. Without one, the drawing for the profile's gender shows. */
function AvatarEditor({ profile, onChange }: { profile: Profile; onChange: (profile: Profile) => void }) {
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<Profile>) {
    setBusy(true)
    setError(null)
    try {
      onChange(await action())
    } catch (caught) {
      if (caught instanceof ApiError) setError(caught.fieldErrors.images ?? caught.fieldErrors.image ?? caught.message)
      else setError('Настана грешка.')
    } finally {
      setBusy(false)
    }
  }

  function upload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    const form = new FormData()
    form.append('image', file)
    void run(() => api<Profile>('/profile/me/avatar', { method: 'POST', body: form }))
  }

  return (
    <div className="avatar-editor">
      <Avatar url={profile.avatarUrl} gender={profile.gender} name={profile.firstName} size={96} />
      <div className="avatar-actions">
        <label className={busy ? 'link-button disabled' : 'link-button'}>
          {profile.avatarUrl ? 'Смени слика' : 'Додај слика'}
          <input type="file" accept="image/jpeg,image/png,image/webp" className="sr-only" disabled={busy} onChange={upload} />
        </label>
        {profile.avatarUrl && (
          <button type="button" className="link-button" disabled={busy}
            onClick={() => void run(() => api<Profile>('/profile/me/avatar', { method: 'DELETE' }))}>
            Отстрани
          </button>
        )}
      </div>
      {error && <small role="alert" className="field-error">{error}</small>}
    </div>
  )
}

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
        <div className="profile-who">
          <AvatarEditor profile={p} onChange={(profile) => setData({ ...data, profile })} />
          <div>
            <p className="eyebrow">Твојот профил</p>
            <h1>{p.firstName}, {p.age}</h1>
            <p className="muted">{p.faculty}</p>
          </div>
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
                {p.situation === 'HAS_PLACE' ? (
                  <Row label="Кирија и локација" value="Од огласот" />
                ) : (
                  <>
                    <Row label="Кирија" value={`${p.budgetMin}–${p.budgetMax} €`} />
                    <Row label="Локации" value={r.areas.filter((a) => p.areas.includes(a.code)).map((a) => a.name).join(', ')} />
                  </>
                )}
              </>
            )}
          </dl>
        </section>
        <section className="card">
          <h2>Навики</h2>
          <dl>
            {p.smoker !== null && <Row label="Пушач" value={yesNo(p.smoker)} />}
            {p.hasPets !== null && <Row label="Миленичиња" value={yesNo(p.hasPets)} />}
            <Row label="Гости" value={LABELS.guestsFrequency[p.guestsFrequency]} />
            <Row label="Чистење" value={LABELS.cleaning[p.cleaning]} />
            <Row label="Спиење" value={LABELS.sleepSchedule[p.sleepSchedule]} />
            <Row label="Музика и звук" value={LABELS.sound[p.sound]} />
          </dl>
        </section>
        <section className="card">
          <h2>Однос со цимерот</h2>
          <dl>
            <Row label="Комуникација" value={LABELS.communication[p.communication]} />
            <Row label="Делење" value={LABELS.sharing[p.sharing]} />
            <Row label="Забави дома" value={LABELS.parties[p.parties]} />
            <Row label="Партнер да преноќува" value={LABELS.partnerStays[p.partnerStays]} />
          </dl>
        </section>
        <section className="card">
          <h2>Цимер</h2>
          <dl>
            {p.acceptsSmoker !== null && <Row label="Цимер пушач" value={yesNo(p.acceptsSmoker)} />}
            {p.acceptsPets !== null && <Row label="Цимер со миленичиња" value={yesNo(p.acceptsPets)} />}
            <Row label="Пол на цимерот" value={LABELS.genderPreference[p.roommateGenderPreference]} />
          </dl>
          <h2 className="subhead">Хобиња</h2>
          <ul className="tags">
            {p.hobbyIds.map((id) => <li key={id}>{nameOf(r.hobbies, id)}</li>)}
          </ul>
        </section>
      </div>
      <DeleteAccount />
    </div>
  )
}
