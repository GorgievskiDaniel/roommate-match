import { useEffect, useState, type KeyboardEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api'
import { useAuth } from '../auth'
import FacultyDistance, { type Faculty } from '../components/FacultyDistance'
import PersonCard, { MessageButton } from '../components/PersonCard'
import { LABELS, wantedRoommate, type Listing } from '../types'

/** Large photos that can be turned with the arrows, the keyboard or the small pictures below. */
export function Carousel({ images }: { images: Listing['images'] }) {
  const [index, setIndex] = useState(0)
  const many = images.length > 1
  const turn = (step: number) => setIndex((current) => (current + step + images.length) % images.length)

  function onKey(event: KeyboardEvent) {
    if (!many) return
    if (event.key === 'ArrowLeft') turn(-1)
    if (event.key === 'ArrowRight') turn(1)
  }

  return (
    <div className="carousel" role="group" aria-label="Слики од огласот" tabIndex={0} onKeyDown={onKey}>
      <div className="carousel-stage">
        <img src={images[index].url} alt={`Слика ${index + 1} од ${images.length}`} />
        {many && (
          <>
            <button type="button" className="carousel-arrow carousel-prev" aria-label="Претходна слика" onClick={() => turn(-1)}>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7" /></svg>
            </button>
            <button type="button" className="carousel-arrow carousel-next" aria-label="Следна слика" onClick={() => turn(1)}>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5l7 7-7 7" /></svg>
            </button>
            <span className="carousel-counter" aria-hidden="true">{index + 1} / {images.length}</span>
          </>
        )}
      </div>
      {many && (
        <div className="carousel-thumbs">
          {images.map((image, i) => (
            <button
              key={image.id}
              type="button"
              className={i === index ? 'thumb selected' : 'thumb'}
              aria-label={`Слика ${i + 1}`}
              aria-current={i === index}
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

export default function ListingDetailPage() {
  const { id } = useParams()
  const { user } = useAuth()
  const [listing, setListing] = useState<Listing | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [faculty, setFaculty] = useState<Faculty | null>(null)

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

  if (error) {
    return (
      <div className="narrow">
        <p role="alert" className="form-error">{error}</p>
        <Link to="/listings">Назад кон огласите</Link>
      </div>
    )
  }
  if (!listing) return <p className="muted">Се вчитува…</p>

  const livesHere = listing.residents.some((person) => person.userId === user?.id)
  const owner = listing.residents.find((person) => person.userId === listing.ownerId)

  return (
    <div className="listing-detail">
      <Link to="/listings" className="back-link">← Сите огласи</Link>
      <div className="listing-detail-grid">
        <div className="listing-detail-main">
          {listing.images.length > 0
            ? <Carousel key={listing.id} images={listing.images} />
            : <p className="notice">Огласот сè уште нема слики.</p>}
          <section className="card">
            <h2>Опис</h2>
            <p className="description">{listing.description}</p>
          </section>
          <section className="card">
            <h2>Кој живее овде</h2>
            {listing.residents.map((person) => <PersonCard key={person.userId} person={person} />)}
          </section>
        </div>
        <aside className="card listing-detail-side">
          <p className="eyebrow">{LABELS.propertyType[listing.propertyType]}</p>
          <h1>{LABELS.propertyType[listing.propertyType]} во {listing.areaName}</h1>
          <p className="muted">{listing.address}</p>
          <p className="listing-detail-price"><strong>{listing.monthlyRent} €</strong> месечно по цимер</p>
          <dl>
            <div className="row"><dt>Тип</dt><dd>{LABELS.propertyType[listing.propertyType]}</dd></div>
            <div className="row"><dt>Локација</dt><dd>{listing.areaName}</dd></div>
            <div className="row"><dt>Се бараат</dt><dd>{listing.freeSpots === 1 ? '1 цимер' : `${listing.freeSpots} цимери`}</dd></div>
            <div className="row"><dt>Пол на цимерот</dt><dd>{wantedRoommate(listing.roommateGender)}</dd></div>
            <div className="row"><dt>Живеат сега</dt><dd>{listing.residents.length}</dd></div>
          </dl>
          {listing.amenities.length > 0 && (
            <>
              <h2 className="subhead">Што нуди</h2>
              <ul className="tags amenity-tags">
                {listing.amenities.map((amenity) => <li key={amenity.code}>{amenity.label}</li>)}
              </ul>
            </>
          )}
          {livesHere
            ? <Link to="/listing" className="button secondary">Уреди го огласот</Link>
            : owner && <MessageButton userId={owner.userId} label={`Испрати порака на ${owner.firstName}`} primary />}
          <Link to={`/?listing=${listing.id}`} className="listing-detail-map">Види на мапа</Link>
          <FacultyDistance path={`/listings/${listing.id}`} selected={faculty} onSelect={setFaculty} />
        </aside>
      </div>
    </div>
  )
}
