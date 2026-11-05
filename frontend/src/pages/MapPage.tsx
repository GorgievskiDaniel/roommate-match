import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useMemo, useState } from 'react'
import { MapContainer, Marker, TileLayer } from 'react-leaflet'
import { useSearchParams } from 'react-router-dom'
import { api } from '../api'
import MapView, { type View } from '../components/MapView'
import PersonCard from '../components/PersonCard'
import { LABELS, type AreaOption, type DormDetail, type Listing, type MapBlock, type MapDorm, type MapListing, type Reference } from '../types'

const MACEDONIA: View = { center: [41.6, 21.7], zoom: 8 }
const SKOPJE: View = { center: [41.99621, 21.43189], zoom: 12 }
const CITY_ZOOM = 13
const MUNICIPALITY_ZOOM = 14
const DORM_ZOOM = 15
// Blocks stand so close together that their points only separate this far in.
const BLOCKS_ZOOM = 19

type Selection = { kind: 'listing'; id: number } | { kind: 'dorm'; id: number; block?: MapBlock }

function pin(kind: 'listing' | 'dorm', label: string, selected: boolean) {
  return L.divIcon({
    className: '',
    html: `<span class="pin pin-${kind}${selected ? ' pin-selected' : ''}">${label}</span>`,
    iconSize: [0, 0],
  })
}

function Gallery({ images }: { images: Listing['images'] }) {
  const [index, setIndex] = useState(0)
  const current = images[Math.min(index, images.length - 1)]
  return (
    <div className="gallery">
      <img src={current.url} alt="Слика од огласот" className="gallery-main" />
      {images.length > 1 && (
        <div className="gallery-thumbs">
          {images.map((image, i) => (
            <button
              key={image.id}
              type="button"
              className={i === index ? 'thumb selected' : 'thumb'}
              aria-label={`Слика ${i + 1}`}
              onClick={() => setIndex(i)}
            >
              <img src={image.url} alt="" />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function ListingPanel({ id }: { id: number }) {
  const [listing, setListing] = useState<Listing | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    setListing(null)
    setError(null)
    api<Listing>(`/listings/${id}`)
      .then((loaded) => active && setListing(loaded))
      .catch((caught: Error) => active && setError(caught.message))
    return () => {
      active = false
    }
  }, [id])

  if (error) return <p role="alert" className="form-error">{error}</p>
  if (!listing) return <p className="muted">Се вчитува…</p>
  return (
    <>
      <Gallery key={listing.id} images={listing.images} />
      <h2>{LABELS.propertyType[listing.propertyType]} во {listing.areaName}</h2>
      <p className="muted">{listing.address}</p>
      <p className="price">
        {listing.monthlyRent} € месечно по цимер · {listing.freeSpots === 1 ? 'се бара 1 цимер' : `се бараат ${listing.freeSpots} цимери`}
      </p>
      <p className="description">{listing.description}</p>
      <h3>Кој живее овде</h3>
      {listing.residents.map((person) => <PersonCard key={person.userId} person={person} />)}
    </>
  )
}

function DormPanel({ id, block }: { id: number; block?: MapBlock }) {
  const [dorm, setDorm] = useState<DormDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    setDorm(null)
    setError(null)
    api<DormDetail>(`/dorms/${id}`)
      .then((loaded) => active && setDorm(loaded))
      .catch((caught: Error) => active && setError(caught.message))
    return () => {
      active = false
    }
  }, [id])

  if (error) return <p role="alert" className="form-error">{error}</p>
  if (!dorm) return <p className="muted">Се вчитува…</p>
  const seekers = block?.roomOption ? dorm.seekers.filter((s) => s.roomOption === block.roomOption) : dorm.seekers
  return (
    <>
      <h2>{dorm.name}{block ? `, ${block.name}` : ''}</h2>
      {block?.roomOption && <p className="muted">Соба: {block.roomOption}</p>}
      <h3>Студенти што бараат цимер</h3>
      {seekers.length === 0 && <p className="muted">Сè уште никој не бара цимер овде.</p>}
      {seekers.map(({ profile, roomOption }) => (
        <PersonCard key={profile.userId} person={profile} note={roomOption} />
      ))}
    </>
  )
}

export default function MapPage() {
  const [data, setData] = useState<{ listings: MapListing[]; dorms: MapDorm[] } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [showApartments, setShowApartments] = useState(true)
  const [showHouses, setShowHouses] = useState(true)
  const [showDorms, setShowDorms] = useState(true)
  // A link from the matches page names the listing to open.
  const [searchParams] = useSearchParams()
  const linkedListing = Number(searchParams.get('listing')) || null
  const [selection, setSelection] = useState<Selection | null>(linkedListing ? { kind: 'listing', id: linkedListing } : null)
  const [place, setPlace] = useState('MACEDONIA')
  const [view, setView] = useState<View>(MACEDONIA)
  // A dorm chosen from the list moves the map away from the place picked above it.
  const [movedAway, setMovedAway] = useState(false)
  const [areas, setAreas] = useState<AreaOption[]>([])

  useEffect(() => {
    let active = true
    api<{ listings: MapListing[]; dorms: MapDorm[] }>('/map')
      .then((loaded) => {
        if (!active) return
        setData(loaded)
        const linked = loaded.listings.find((listing) => listing.id === linkedListing)
        if (linked) {
          setMovedAway(true)
          setView({ center: [linked.latitude, linked.longitude], zoom: DORM_ZOOM })
        }
      })
      .catch((caught: Error) => active && setError(caught.message))
    api<Reference>('/reference')
      .then((reference) => active && setAreas(reference.areas))
      .catch((caught: Error) => active && setError(caught.message))
    return () => {
      active = false
    }
  }, [linkedListing])

  function choosePlace(code: string) {
    setPlace(code)
    setMovedAway(false)
    if (code === 'MACEDONIA') return setView(MACEDONIA)
    if (code === 'SKOPJE') return setView(SKOPJE)
    const area = areas.find((candidate) => candidate.code === code)
    if (area) {
      setView({ center: [area.latitude, area.longitude], zoom: area.skopje ? MUNICIPALITY_ZOOM : CITY_ZOOM })
    }
  }

  const dormsOnMap = useMemo(
    () => data?.dorms.filter((d) => d.blocks.length === 0 && d.latitude !== null && d.longitude !== null) ?? [],
    [data],
  )

  const isSelected = (kind: Selection['kind'], id: number) => selection?.kind === kind && selection.id === id
  const isBlockSelected = (dorm: MapDorm, block: MapBlock) =>
    selection?.kind === 'dorm' && selection.id === dorm.id && selection.block?.name === block.name

  function showDorm(dorm: MapDorm) {
    setSelection({ kind: 'dorm', id: dorm.id })
    const { blocks } = dorm
    if (blocks.length > 0) {
      setMovedAway(true)
      setView({
        center: [
          blocks.reduce((sum, b) => sum + b.latitude, 0) / blocks.length,
          blocks.reduce((sum, b) => sum + b.longitude, 0) / blocks.length,
        ],
        zoom: BLOCKS_ZOOM,
      })
    } else if (dorm.latitude !== null && dorm.longitude !== null) {
      setMovedAway(true)
      setView({ center: [dorm.latitude, dorm.longitude], zoom: DORM_ZOOM })
    }
  }

  return (
    <div className="map-page">
      <div className="map-column">
        <div className="map-toolbar">
          <h1>Каде се бара цимер</h1>
          <div className="choices">
            <label className={showApartments ? 'choice selected' : 'choice'}>
              <input type="checkbox" checked={showApartments} onChange={() => setShowApartments(!showApartments)} />
              Станови
            </label>
            <label className={showHouses ? 'choice selected' : 'choice'}>
              <input type="checkbox" checked={showHouses} onChange={() => setShowHouses(!showHouses)} />
              Куќи
            </label>
            <label className={showDorms ? 'choice selected' : 'choice'}>
              <input type="checkbox" checked={showDorms} onChange={() => setShowDorms(!showDorms)} />
              Студентски домови
            </label>
          </div>
        </div>
        <div className="place-row">
        <label className="field place-select">
          <span>Покажи на мапата</span>
          <select value={place} onChange={(e) => choosePlace(e.target.value)}>
            <option value="MACEDONIA">Цела Македонија</option>
            <option value="SKOPJE">Скопје</option>
            <optgroup label="Општини во Скопје">
              {areas.filter((a) => a.skopje).map((a) => <option key={a.code} value={a.code}>{a.name}</option>)}
            </optgroup>
            <optgroup label="Други градови">
              {areas.filter((a) => !a.skopje).map((a) => <option key={a.code} value={a.code}>{a.name}</option>)}
            </optgroup>
          </select>
        </label>
        {movedAway && (
          <button type="button" className="button secondary" onClick={() => choosePlace(place)}>
            Врати го приказот
          </button>
        )}
        </div>
        {error && <p role="alert" className="form-error">{error}</p>}
        <div className="map-frame">
          <MapContainer center={MACEDONIA.center} zoom={MACEDONIA.zoom} className="map">
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
              maxZoom={BLOCKS_ZOOM}
            />
            <MapView view={view} />
            {data?.listings.filter((l) => (l.propertyType === 'HOUSE' ? showHouses : showApartments)).map((listing) => (
              <Marker
                key={`listing-${listing.id}`}
                position={[listing.latitude, listing.longitude]}
                title={`${LABELS.propertyType[listing.propertyType]} во ${listing.areaName}`}
                icon={pin('listing', `${listing.monthlyRent} €`, isSelected('listing', listing.id))}
                eventHandlers={{ click: () => setSelection({ kind: 'listing', id: listing.id }) }}
              />
            ))}
            {showDorms && data?.dorms.flatMap((dorm) => dorm.blocks.map((block) => (
              <Marker
                key={`block-${dorm.id}-${block.name}`}
                position={[block.latitude, block.longitude]}
                riseOnHover
                title={`${dorm.name}, ${block.name}`}
                icon={pin('dorm', `${dorm.name.replace(/^[ДП]СД /, '')} ${block.name} · ${block.seekers}`, isBlockSelected(dorm, block))}
                eventHandlers={{ click: () => setSelection({ kind: 'dorm', id: dorm.id, block }) }}
              />
            )))}
            {showDorms && dormsOnMap.map((dorm) => (
              <Marker
                key={`dorm-${dorm.id}`}
                position={[dorm.latitude!, dorm.longitude!]}
                riseOnHover
                title={dorm.name}
                icon={pin('dorm', `${dorm.name} · ${dorm.seekers}`, isSelected('dorm', dorm.id))}
                eventHandlers={{ click: () => setSelection({ kind: 'dorm', id: dorm.id }) }}
              />
            ))}
          </MapContainer>
        </div>
        {showDorms && data && (
          <section className="dorm-list">
            <h2>Студентски домови (бараат цимер)</h2>
            <div className="choices">
              {data.dorms.map((dorm) => (
                <button
                  key={dorm.id}
                  type="button"
                  className={isSelected('dorm', dorm.id) ? 'choice selected' : 'choice'}
                  onClick={() => showDorm(dorm)}
                >
                  {dorm.name} ({dorm.seekers})
                </button>
              ))}
            </div>
          </section>
        )}
      </div>
      <aside className="detail-panel card" aria-live="polite">
        {!selection && <p className="muted">Избери точка на мапата за да ги видиш деталите.</p>}
        {selection?.kind === 'listing' && <ListingPanel id={selection.id} />}
        {selection?.kind === 'dorm' && <DormPanel id={selection.id} block={selection.block} />}
      </aside>
    </div>
  )
}
