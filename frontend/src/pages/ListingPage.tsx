import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { MapContainer, Marker, TileLayer, useMapEvents } from 'react-leaflet'
import { api, ApiError } from '../api'
import { useAuth } from '../auth'
import AddressField, { type AddressSuggestion } from '../components/AddressField'
import DormPostEditor from '../components/DormPostEditor'
import MapView, { type View } from '../components/MapView'
import PersonCard from '../components/PersonCard'
import { AMENITIES, LABELS, type AmenityCode, type Listing, type Profile, type PropertyType, type Reference } from '../types'

const fits = (code: AmenityCode, type: PropertyType) => {
  const only = AMENITIES.find((amenity) => amenity.code === code)?.only
  return !only || only === type
}

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
  amenities: AmenityCode[]
}

const EMPTY: Draft = {
  propertyType: null, address: '', area: null, latitude: null, longitude: null,
  monthlyRent: '', freeSpots: '1', description: '', amenities: [],
}

function ClickToPick({ onPick }: { onPick: (latitude: number, longitude: number) => void }) {
  useMapEvents({ click: (event) => onPick(event.latlng.lat, event.latlng.lng) })
  return null
}

function ListingForm(props: { reference: Reference; existing: Listing | null; onSaved: (listing: Listing) => void; onCancel?: () => void }) {
  const { existing } = props
  const [draft, setDraft] = useState<Draft>(existing
    ? {
        ...existing,
        monthlyRent: String(existing.monthlyRent),
        freeSpots: String(existing.freeSpots),
        amenities: existing.amenities.map((amenity) => amenity.code),
      }
    : EMPTY)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [view, setView] = useState<View | null>(null)
  // After an address is picked: is its point on the map right? "no" asks for a click on the map.
  const [placement, setPlacement] = useState<'asking' | 'yes' | 'no' | null>(null)

  function pickAddress(suggestion: AddressSuggestion) {
    // The area the point falls in, unless one is already chosen.
    let nearest = props.reference.areas[0]
    for (const area of props.reference.areas) {
      const distance = (area.latitude - suggestion.latitude) ** 2 + (area.longitude - suggestion.longitude) ** 2
      if (distance < (nearest.latitude - suggestion.latitude) ** 2 + (nearest.longitude - suggestion.longitude) ** 2) {
        nearest = area
      }
    }
    setDraft((current) => ({
      ...current,
      address: suggestion.label,
      latitude: suggestion.latitude,
      longitude: suggestion.longitude,
      area: current.area ?? nearest?.code ?? null,
    }))
    setView({ center: [suggestion.latitude, suggestion.longitude], zoom: 17 })
    setPlacement('asking')
  }

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
      <h2>{existing ? 'Измени го огласот' : 'Нов оглас за стан или куќа'}</h2>
      {formError && <p role="alert" className="form-error">{formError}</p>}
      <fieldset className="field">
        <legend>Тип на живеалиште</legend>
        <div className="choices">
          {(Object.keys(LABELS.propertyType) as PropertyType[]).map((type) => (
            <label key={type} className={draft.propertyType === type ? 'choice selected' : 'choice'}>
              <input type="radio" name="propertyType" checked={draft.propertyType === type} onChange={() => setDraft((current) => ({
                ...current,
                propertyType: type,
                // A flat has no yard and a house no lift, so those choices go when the type changes.
                amenities: current.amenities.filter((code) => fits(code, type)),
              }))} />
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
      <AddressField value={draft.address} onChange={(value) => set('address', value)} onPick={pickAddress} error={errors.address} />
      <div className="field">
        <span className="field-label">Локација: кликни на твојот стан на мапата</span>
        <div className="picker-frame">
          <MapContainer center={picked ?? MACEDONIA} zoom={picked ? 15 : 8} className="map">
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <MapView view={view} />
            <ClickToPick onPick={(latitude, longitude) => {
              setDraft((current) => ({ ...current, latitude, longitude }))
              if (placement === 'no' || placement === 'asking') setPlacement('yes')
            }} />
            {picked && <Marker position={picked} icon={PICKED_PIN} />}
          </MapContainer>
        </div>
        {placement === 'asking' && (
          <div className="placement-check" role="group" aria-label="Проверка на локацијата">
            <span>Дали адресата ја прикажува точно на мапата?</span>
            <div className="placement-buttons">
              <button type="button" className="button secondary" onClick={() => setPlacement('yes')}>Да</button>
              <button type="button" className="button secondary" onClick={() => setPlacement('no')}>Не</button>
            </div>
          </div>
        )}
        {placement === 'no' && (
          <p role="alert" className="notice">Ве молиме одберете ја точно адресата на мапата.</p>
        )}
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
      <fieldset className="field">
        <legend>Што нуди</legend>
        <div className="choices">
          {AMENITIES.filter((amenity) => !draft.propertyType || fits(amenity.code, draft.propertyType)).map((amenity) => {
            const checked = draft.amenities.includes(amenity.code)
            return (
              <label key={amenity.code} className={checked ? 'choice selected' : 'choice'}>
                <input type="checkbox" checked={checked} onChange={() => set('amenities', checked
                  ? draft.amenities.filter((code) => code !== amenity.code)
                  : [...draft.amenities, amenity.code])} />
                {amenity.label}
              </label>
            )
          })}
        </div>
        {errors.amenities && <small className="field-error">{errors.amenities}</small>}
      </fieldset>
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
          <p className="muted">
            {listing.address} · {listing.monthlyRent} € по цимер
            {listing.amenities.length > 0 && ` · ${listing.amenities.map((amenity) => amenity.label.toLowerCase()).join(', ')}`}
          </p>
          <Link to={`/listings/${listing.id}`}>Види како го гледаат другите</Link>
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
        {isOwner && listing.images.length > 1 && (
          <p className="muted">Главната слика е првата што ја гледаат другите, на картичката и на мапата.</p>
        )}
        <div className="image-grid">
          {listing.images.map((image, index) => (
            <figure key={image.id} className={index === 0 ? 'main-photo' : undefined}>
              <img src={image.url} alt={index === 0 ? 'Главна слика од станот' : 'Слика од станот'} />
              {index === 0 && <span className="main-photo-badge">Главна</span>}
              {isOwner && (
                <div className="image-actions">
                  {index > 0 && (
                    <button type="button" className="link-button" onClick={() => void run(() => api<Listing>(`/listings/${listing.id}/images/${image.id}/cover`, { method: 'PUT' }))}>
                      Постави како главна
                    </button>
                  )}
                  <button type="button" className="link-button" onClick={() => void run(() => api<Listing>(`/listings/${listing.id}/images/${image.id}`, { method: 'DELETE' }))}>
                    Избриши
                  </button>
                </div>
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
  const [chosenTab, setChosenTab] = useState<'place' | 'dorm' | null>(null)

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

  // Everyone may post either kind of ad; the tab opens on the one that fits the profile.
  const tab = chosenTab ?? (state.profile.housingType === 'DORM' ? 'dorm' : 'place')

  return (
    <div className="my-ads">
      <div className="ad-tabs" role="tablist" aria-label="Вид на оглас">
        <button type="button" role="tab" aria-selected={tab === 'place'} className={tab === 'place' ? 'ad-tab selected' : 'ad-tab'}
          onClick={() => setChosenTab('place')}>
          Стан или куќа
        </button>
        <button type="button" role="tab" aria-selected={tab === 'dorm'} className={tab === 'dorm' ? 'ad-tab selected' : 'ad-tab'}
          onClick={() => setChosenTab('dorm')}>
          Соба во студентски дом
        </button>
      </div>
      {tab === 'dorm' ? (
        <DormPostEditor dorms={state.reference.dorms} defaultDormId={state.profile.dormId} />
      ) : state.listing && !editing ? (
        <ListingManager listing={state.listing} userId={user.id} onChange={setListing} onEdit={() => setEditing(true)} />
      ) : state.listing ? (
        <ListingForm reference={state.reference} existing={state.listing} onSaved={setListing} onCancel={() => setEditing(false)} />
      ) : (
        <div className="listing-start">
          <ListingForm reference={state.reference} existing={null} onSaved={setListing} />
          <JoinForm onJoined={setListing} />
        </div>
      )}
    </div>
  )
}
