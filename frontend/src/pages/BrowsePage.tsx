import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import { AMENITIES, LABELS, type Amenity, type AmenityCode, type AreaOption, type BrowseDorm, type BrowseListing, type Reference } from '../types'

type Kind = 'APARTMENT' | 'HOUSE' | 'DORM'

const KINDS: { kind: Kind; label: string }[] = [
  { kind: 'APARTMENT', label: 'Станови' },
  { kind: 'HOUSE', label: 'Куќи' },
  { kind: 'DORM', label: 'Студентски домови' },
]

/** A dorm has no municipality of its own, so it counts for the one whose centre is closest. */
function nearestArea(dorm: BrowseDorm, areas: AreaOption[]): AreaOption | null {
  if (dorm.latitude === null || dorm.longitude === null) return null
  let best: AreaOption | null = null
  let bestDistance = Infinity
  for (const area of areas) {
    const distance = (area.latitude - dorm.latitude) ** 2 + (area.longitude - dorm.longitude) ** 2
    if (distance < bestDistance) {
      best = area
      bestDistance = distance
    }
  }
  return best
}

function Amenities({ amenities }: { amenities: Amenity[] }) {
  if (amenities.length === 0) return null
  const shown = amenities.slice(0, 4)
  return (
    <ul className="tags amenity-tags">
      {shown.map((amenity) => <li key={amenity.code}>{amenity.label}</li>)}
      {amenities.length > shown.length && <li>+{amenities.length - shown.length}</li>}
    </ul>
  )
}

export default function BrowsePage() {
  const [data, setData] = useState<{ listings: BrowseListing[]; dorms: BrowseDorm[] } | null>(null)
  const [reference, setReference] = useState<Reference | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [kinds, setKinds] = useState<Kind[]>(['APARTMENT', 'HOUSE', 'DORM'])
  const [place, setPlace] = useState('')
  const [minRent, setMinRent] = useState('')
  const [maxRent, setMaxRent] = useState('')
  const [wanted, setWanted] = useState<AmenityCode[]>([])

  useEffect(() => {
    let active = true
    Promise.all([api<{ listings: BrowseListing[]; dorms: BrowseDorm[] }>('/browse'), api<Reference>('/reference')])
      .then(([loaded, loadedReference]) => {
        if (!active) return
        setData(loaded)
        setReference(loadedReference)
      })
      .catch((caught: Error) => active && setError(caught.message))
    return () => {
      active = false
    }
  }, [])

  const areas = reference?.areas ?? []
  const dormAreas = useMemo(
    () => new Map((data?.dorms ?? []).map((dorm) => [dorm.id, nearestArea(dorm, areas)])),
    [data, areas],
  )

  const inPlace = (code: string | undefined, skopje: boolean | undefined) =>
    place === '' || (place === 'SKOPJE' ? skopje === true : code === place)
  // A listing must offer everything that is ticked.
  const offers = (own: Amenity[]) => wanted.every((code) => own.some((amenity) => amenity.code === code))
  // Rent and amenities are only known for flats and houses.
  const rentOrAmenities = minRent !== '' || maxRent !== '' || wanted.length > 0

  const listings = (data?.listings ?? []).filter((listing) =>
    kinds.includes(listing.propertyType)
    && inPlace(listing.area, areas.find((area) => area.code === listing.area)?.skopje)
    && (minRent === '' || listing.monthlyRent >= Number(minRent))
    && (maxRent === '' || listing.monthlyRent <= Number(maxRent))
    && offers(listing.amenities))

  const dorms = (data?.dorms ?? []).filter((dorm) => {
    const area = dormAreas.get(dorm.id)
    return kinds.includes('DORM') && !rentOrAmenities && inPlace(area?.code, area?.skopje)
  })

  const toggle = <T,>(list: T[], item: T) => (list.includes(item) ? list.filter((other) => other !== item) : [...list, item])
  const filtered = kinds.length < KINDS.length || place !== '' || rentOrAmenities

  function clear() {
    setKinds(KINDS.map(({ kind }) => kind))
    setPlace('')
    setMinRent('')
    setMaxRent('')
    setWanted([])
  }

  if (error) return <p role="alert" className="form-error">{error}</p>
  if (!data || !reference) return <p className="muted">Се вчитува…</p>

  const total = listings.length + dorms.length

  return (
    <div className="browse-page">
      <aside className="card browse-filters" aria-label="Филтри">
        <div className="browse-filters-head">
          <h2>Филтри</h2>
          {filtered && <button type="button" className="link-button" onClick={clear}>Исчисти</button>}
        </div>
        <fieldset className="field">
          <legend>Тип</legend>
          <div className="choices">
            {KINDS.map(({ kind, label }) => (
              <label key={kind} className={kinds.includes(kind) ? 'choice selected' : 'choice'}>
                <input type="checkbox" checked={kinds.includes(kind)} onChange={() => setKinds(toggle(kinds, kind))} />
                {label}
              </label>
            ))}
          </div>
        </fieldset>
        <label className="field">
          <span>Локација</span>
          <select value={place} onChange={(event) => setPlace(event.target.value)}>
            <option value="">Цела Македонија</option>
            <option value="SKOPJE">Скопје (сите општини)</option>
            <optgroup label="Општини во Скопје">
              {areas.filter((area) => area.skopje).map((area) => <option key={area.code} value={area.code}>{area.name}</option>)}
            </optgroup>
            <optgroup label="Други градови">
              {areas.filter((area) => !area.skopje).map((area) => <option key={area.code} value={area.code}>{area.name}</option>)}
            </optgroup>
          </select>
        </label>
        <fieldset className="field">
          <legend>Кирија по цимер (€)</legend>
          <div className="field-row">
            <label>
              <span className="sr-only">Најмалку</span>
              <input type="number" min={0} inputMode="numeric" placeholder="од" value={minRent} onChange={(event) => setMinRent(event.target.value)} />
            </label>
            <label>
              <span className="sr-only">Најмногу</span>
              <input type="number" min={0} inputMode="numeric" placeholder="до" value={maxRent} onChange={(event) => setMaxRent(event.target.value)} />
            </label>
          </div>
        </fieldset>
        <fieldset className="field">
          <legend>Што нуди</legend>
          <div className="choices">
            {AMENITIES.map((amenity) => (
              <label key={amenity.code} className={wanted.includes(amenity.code) ? 'choice selected' : 'choice'}>
                <input type="checkbox" checked={wanted.includes(amenity.code)} onChange={() => setWanted(toggle(wanted, amenity.code))} />
                {amenity.label}
              </label>
            ))}
          </div>
        </fieldset>
        {rentOrAmenities && kinds.includes('DORM') && (
          <p className="muted browse-note">Домовите немаат кирија и бенефиции во огласот, па не се прикажуваат со овие филтри.</p>
        )}
      </aside>

      <section className="browse-results">
        <div className="browse-head">
          <h1>Огласи</h1>
          <p className="muted" aria-live="polite">
            {total === 0 ? 'Нема огласи со овие филтри.' : `Прикажани: ${total}`}
          </p>
        </div>
        <ul className="browse-grid">
          {listings.map((listing) => (
            <li key={`listing-${listing.id}`}>
              <Link to={`/listings/${listing.id}`} className="browse-card">
                <div className="browse-photo">
                  <img src={listing.coverImageUrl} alt="" loading="lazy" />
                  <span className="browse-kind">{LABELS.propertyType[listing.propertyType]}</span>
                  {listing.imageCount > 1 && <span className="browse-count">{listing.imageCount} слики</span>}
                </div>
                <div className="browse-body">
                  <h2>{LABELS.propertyType[listing.propertyType]} во {listing.areaName}</h2>
                  <p className="muted">{listing.address}</p>
                  <p className="browse-price"><strong>{listing.monthlyRent} €</strong> месечно по цимер</p>
                  <ul className="facts">
                    <li>{listing.freeSpots === 1 ? 'Се бара 1 цимер' : `Се бараат ${listing.freeSpots} цимери`}</li>
                    <li>{listing.residents === 1 ? 'Живее 1 студент' : `Живеат ${listing.residents} студенти`}</li>
                  </ul>
                  <Amenities amenities={listing.amenities} />
                </div>
              </Link>
            </li>
          ))}
          {dorms.map((dorm) => (
            <li key={`dorm-${dorm.id}`}>
              <Link to={`/dorms/${dorm.id}`} className="browse-card">
                <div className={dorm.coverPhotoUrl ? 'browse-photo' : 'browse-photo browse-dorm'}>
                  {dorm.coverPhotoUrl
                    ? <img src={dorm.coverPhotoUrl} alt="" loading="lazy" />
                    : <span className="browse-dorm-name">{dorm.name}</span>}
                  <span className="browse-kind">Студентски дом</span>
                  {dorm.photoCount > 1 && <span className="browse-count">{dorm.photoCount} слики</span>}
                </div>
                <div className="browse-body">
                  <h2>{dorm.name}</h2>
                  <p className="muted">{dorm.neighbourhood ?? dormAreas.get(dorm.id)?.name ?? 'Студентски дом'}</p>
                  {dorm.openingNote && <p className="opening-note">{dorm.openingNote}</p>}
                  <ul className="facts">
                    <li>
                      {dorm.seekers === 0
                        ? 'Сè уште никој не бара цимер'
                        : dorm.seekers === 1 ? '1 студент бара цимер' : `${dorm.seekers} студенти бараат цимер`}
                    </li>
                  </ul>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
