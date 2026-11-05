import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { MapContainer, Marker, TileLayer, useMapEvents } from 'react-leaflet'
import { api, ApiError } from '../api'
import { useAuth } from '../auth'
import MapView, { type View } from '../components/MapView'
import PersonCard from '../components/PersonCard'
import { LABELS, type Listing, type Profile, type PropertyType, type Reference } from '../types'

const MACEDONIA: [number, number] = [41.6, 21.7]
const PICKED_PIN = L.divIcon({ className: '', html: '<span class="pin pin-listing">Тука</span>', iconSize: [0, 0] })

interface Draft {
  propertyType: PropertyType | null
  address: string
  area: string | null
  latitude: number | null
  longitude: number | null
  monthlyRent: string
  freeSpots: string
  description: string
}

const EMPTY: Draft = {
  propertyType: null, address: '', area: null, latitude: null, longitude: null,
  monthlyRent: '', freeSpots: '1', description: '',
}

function ClickToPick({ onPick }: { onPick: (latitude: number, longitude: number) => void }) {
  useMapEvents({ click: (event) => onPick(event.latlng.lat, event.latlng.lng) })
  return null
}

function ListingForm(props: { reference: Reference; existing: Listing | null; onSaved: (listing: Listing) => void; onCancel?: () => void }) {
  const { existing } = props
  const [draft, setDraft] = useState<Draft>(existing
    ? { ...existing, monthlyRent: String(existing.monthlyRent), freeSpots: String(existing.freeSpots) }
    : EMPTY)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [view, setView] = useState<View | null>(null)

  const set = <K extends keyof Draft>(key: K, value: Draft[K]) => setDraft((current) => ({ ...current, [key]: value }))
  const picked: [number, number] | null = draft.latitude !== null && draft.longitude !== null
    ? [draft.latitude, draft.longitude] : null

  async function submit(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setErrors({})
    setFormError(null)
    const body = {
      ...draft,
      monthlyRent: draft.monthlyRent === '' ? null : Number(draft.monthlyRent),
      freeSpots: draft.freeSpots === '' ? null : Number(draft.freeSpots),
    }
    try {
      props.onSaved(await api<Listing>(existing ? `/listings/${existing.id}` : '/listings', {
        method: existing ? 'PUT' : 'POST',
        body,
      }))
    } catch (caught) {
      setFormError(caught instanceof Error ? caught.message : 'Настана грешка.')
      if (caught instanceof ApiError) setErrors(caught.fieldErrors)
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="card listing-form" onSubmit={submit} noValidate>
      <h2>{existing ? 'Измени го огласот' : 'Нов оглас за твојот стан'}</h2>
      {formError && <p role="alert" className="form-error">{formError}</p>}
      <fieldset className="field">
        <legend>Тип на живеалиште</legend>
        <div className="choices">
          {(Object.keys(LABELS.propertyType) as PropertyType[]).map((type) => (
            <label key={type} className={draft.propertyType === type ? 'choice selected' : 'choice'}>
              <input type="radio" name="propertyType" checked={draft.propertyType === type} onChange={() => set('propertyType', type)} />
              {LABELS.propertyType[type]}
            </label>
          ))}
        </div>
        {errors.propertyType && <small className="field-error">{errors.propertyType}</small>}
      </fieldset>
      {[true, false].map((skopje) => (
        <fieldset className="field" key={String(skopje)}>
          <legend>{skopje ? 'Општина во Скопје' : 'или друг град'}</legend>
          <div className="choices">
            {props.reference.areas.filter((area) => area.skopje === skopje).map((area) => (
              <label key={area.code} className={draft.area === area.code ? 'choice selected' : 'choice'}>
                <input type="radio" name="area" checked={draft.area === area.code} onChange={() => {
                  set('area', area.code)
                  setView({ center: [area.latitude, area.longitude], zoom: 14 })
                }} />
                {area.name}
              </label>
            ))}
          </div>
          {!skopje && errors.area && <small className="field-error">{errors.area}</small>}
        </fieldset>
      ))}
      <label className="field">
        <span>Адреса</span>
        <input value={draft.address} maxLength={200} onChange={(e) => set('address', e.target.value)} />
        <small className="muted">Адресата и точната локација се видливи за сите најавени корисници.</small>
        {errors.address && <small className="field-error">{errors.address}</small>}
      </label>
      <div className="field">
        <span className="field-label">Локација: кликни на твојот стан на мапата</span>
        <div className="picker-frame">
          <MapContainer center={picked ?? MACEDONIA} zoom={picked ? 15 : 8} className="map">
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <MapView view={view} />
            <ClickToPick onPick={(latitude, longitude) => setDraft((current) => ({ ...current, latitude, longitude }))} />
            {picked && <Marker position={picked} icon={PICKED_PIN} />}
          </MapContainer>
        </div>
        {(errors.latitude || errors.longitude) && <small className="field-error">{errors.latitude ?? errors.longitude}</small>}
      </div>
      <div className="field-row">
        <label className="field">
          <span>Кирија по цимер (€)</span>
          <input type="number" min={0} inputMode="numeric" value={draft.monthlyRent} onChange={(e) => set('monthlyRent', e.target.value)} />
          {errors.monthlyRent && <small className="field-error">{errors.monthlyRent}</small>}
        </label>
        <label className="field">
          <span>Колку цимери бараш</span>
          <input type="number" min={1} max={10} inputMode="numeric" value={draft.freeSpots} onChange={(e) => set('freeSpots', e.target.value)} />
          {errors.freeSpots && <small className="field-error">{errors.freeSpots}</small>}
        </label>
      </div>
      <label className="field">
        <span>Опис</span>
        <textarea rows={5} maxLength={1000} value={draft.description} onChange={(e) => set('description', e.target.value)} />
        {errors.description && <small className="field-error">{errors.description}</small>}
      </label>
      <div className="form-actions">
        {props.onCancel && <button type="button" className="button secondary" onClick={props.onCancel}>Откажи</button>}
        <button type="submit" className="button" disabled={saving}>{existing ? 'Зачувај' : 'Создај оглас'}</button>
      </div>
    </form>
  )
}

function JoinForm({ onJoined }: { onJoined: (listing: Listing) => void }) {
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      onJoined(await api<Listing>('/listings/join', { method: 'POST', body: { code } }))
    } catch (caught) {
      if (caught instanceof ApiError) setError(caught.fieldErrors.code ?? caught.message)
      else setError('Настана грешка.')
    }
  }

  return (
    <form className="card join-form" onSubmit={submit} noValidate>
      <h2>Веќе живееш во стан со оглас?</h2>
      <p className="muted">Внеси го кодот од цимерот што го направил огласот.</p>
      <label className="field">
        <span>Код за покана</span>
        <input value={code} maxLength={12} autoComplete="off" onChange={(e) => setCode(e.target.value)} />
        {error && <small className="field-error">{error}</small>}
      </label>
      <button type="submit" className="button secondary">Приклучи се</button>
    </form>
  )
}

function ListingManager(props: { listing: Listing; userId: number; onChange: (listing: Listing | null) => void; onEdit: () => void }) {
  const { listing, userId, onChange } = props
  const isOwner = listing.ownerId === userId
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  async function run(action: () => Promise<Listing | null>) {
    setError(null)
    try {
      onChange(await action())
    } catch (caught) {
      if (caught instanceof ApiError) setError(caught.fieldErrors.images ?? caught.message)
      else setError('Настана грешка.')
    }
  }

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const files = [...(event.target.files ?? [])]
    event.target.value = ''
    if (files.length === 0) return
    const form = new FormData()
    files.forEach((file) => form.append('images', file))
    setUploading(true)
    await run(() => api<Listing>(`/listings/${listing.id}/images`, { method: 'POST', body: form }))
    setUploading(false)
  }

  return (
    <div className="listing-manager">
      <div className="profile-head">
        <div>
          <p className="eyebrow">Мој оглас</p>
          <h1>{LABELS.propertyType[listing.propertyType]} во {listing.areaName}</h1>
          <p className="muted">{listing.address} · {listing.monthlyRent} € по цимер</p>
        </div>
        {isOwner && <button type="button" className="button secondary" onClick={props.onEdit}>Измени</button>}
      </div>
      {error && <p role="alert" className="form-error">{error}</p>}
      {listing.images.length === 0 && (
        <p className="notice">Огласот не се гледа на мапата додека не прикачиш барем една слика.</p>
      )}
      <p className="description">{listing.description}</p>

      <section className="card">
        <h2>Слики</h2>
        <div className="image-grid">
          {listing.images.map((image) => (
            <figure key={image.id}>
              <img src={image.url} alt="Слика од станот" />
              {isOwner && (
                <button type="button" className="link-button" onClick={() => void run(() => api<Listing>(`/listings/${listing.id}/images/${image.id}`, { method: 'DELETE' }))}>
                  Избриши
                </button>
              )}
            </figure>
          ))}
        </div>
        {isOwner && (
          <label className="field upload">
            <span>Додај слики (JPG, PNG или WebP, до 5 MB, најмногу 10)</span>
            <input type="file" accept="image/jpeg,image/png,image/webp" multiple disabled={uploading} onChange={(e) => void upload(e)} />
          </label>
        )}
      </section>

      <section className="card">
        <h2>Станари</h2>
        {listing.inviteCode && (
          <p>Код за покана: <strong className="code">{listing.inviteCode}</strong>. Дај им го на цимерите што веќе живеат со тебе.</p>
        )}
        {listing.residents.map((person) => (
          <div key={person.userId}>
            <PersonCard person={person} />
            {isOwner && person.userId !== userId && (
              <button type="button" className="link-button" onClick={() => void run(() => api<Listing>(`/listings/${listing.id}/residents/${person.userId}`, { method: 'DELETE' }))}>
                Отстрани од огласот
              </button>
            )}
          </div>
        ))}
      </section>

      <div className="form-actions">
        {isOwner ? (
          <button type="button" className="button danger" onClick={() => {
            if (window.confirm('Да го избришам огласот заедно со сликите?')) {
              void run(() => api<void>(`/listings/${listing.id}`, { method: 'DELETE' }).then(() => null))
            }
          }}>
            Избриши го огласот
          </button>
        ) : (
          <button type="button" className="button danger" onClick={() => void run(() => api<void>(`/listings/${listing.id}/leave`, { method: 'POST' }).then(() => null))}>
            Напушти го огласот
          </button>
        )}
      </div>
    </div>
  )
}

export default function ListingPage() {
  const { user } = useAuth()
  const [state, setState] = useState<{ profile: Profile; reference: Reference; listing: Listing | null } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)

  useEffect(() => {
    let active = true
    const mine = api<Listing>('/listings/mine').catch((caught) => {
      if (caught instanceof ApiError && caught.status === 404) return null
      throw caught
    })
    Promise.all([api<Profile>('/profile/me'), api<Reference>('/reference'), mine])
      .then(([profile, reference, listing]) => active && setState({ profile, reference, listing }))
      .catch((caught: Error) => active && setError(caught.message))
    return () => {
      active = false
    }
  }, [])

  if (error) return <p role="alert" className="form-error">{error}</p>
  if (!state || !user) return <p className="muted">Се вчитува…</p>

  const setListing = (listing: Listing | null) => {
    setEditing(false)
    setState({ ...state, listing })
  }

  if (state.listing && !editing) {
    return <ListingManager listing={state.listing} userId={user.id} onChange={setListing} onEdit={() => setEditing(true)} />
  }
  if (state.listing) {
    return <ListingForm reference={state.reference} existing={state.listing} onSaved={setListing} onCancel={() => setEditing(false)} />
  }
  if (state.profile.situation !== 'HAS_PLACE') {
    return (
      <div className="card narrow">
        <h1>Огласите се за оние што имаат стан</h1>
        <p className="muted">Ако имаш стан и бараш цимер, смени ја положбата во профилот.</p>
        <Link to="/profile/edit" className="button secondary">Измени го профилот</Link>
      </div>
    )
  }
  return (
    <div className="listing-start">
      <ListingForm reference={state.reference} existing={null} onSaved={setListing} />
      <JoinForm onJoined={setListing} />
    </div>
  )
}
