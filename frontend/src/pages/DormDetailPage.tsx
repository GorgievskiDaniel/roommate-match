import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api'
import FacultyDistance, { type Faculty } from '../components/FacultyDistance'
import PersonCard from '../components/PersonCard'
import { Carousel } from './ListingDetailPage'
import type { DormDetail } from '../types'

/** A dorm has no photos or rent of its own, so its page is about the students looking for a roommate there. */
export default function DormDetailPage() {
  const { id } = useParams()
  const [dorm, setDorm] = useState<DormDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [faculty, setFaculty] = useState<Faculty | null>(null)

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

  if (error) {
    return (
      <div className="narrow">
        <p role="alert" className="form-error">{error}</p>
        <Link to="/listings">Назад кон огласите</Link>
      </div>
    )
  }
  if (!dorm) return <p className="muted">Се вчитува…</p>

  return (
    <div className="listing-detail">
      <Link to="/listings" className="back-link">← Сите огласи</Link>
      <div className="listing-detail-grid">
        <div className="listing-detail-main">
          {dorm.photoUrls.length > 0 && <Carousel key={dorm.id} images={dorm.photoUrls.map((url, index) => ({ id: index, url }))} />}
          <section className="card">
            <h2>Студенти што бараат цимер</h2>
            {dorm.seekers.length === 0 && <p className="muted">Сè уште никој не бара цимер овде.</p>}
            {dorm.seekers.map(({ profile, roomOption, post }) => (
              <PersonCard key={profile.userId} person={profile} note={roomOption} post={post} />
            ))}
          </section>
        </div>
        <aside className="card listing-detail-side">
          <p className="eyebrow">Студентски дом{dorm.neighbourhood ? ` · ${dorm.neighbourhood}` : ''}</p>
          <h1>{dorm.name}</h1>
          {dorm.openingNote && <p className="opening-note">{dorm.openingNote}</p>}
          <dl>
            <div className="row"><dt>Бараат цимер</dt><dd>{dorm.seekers.length}</dd></div>
          </dl>
          <p className="muted">За да пишеш некому, избери го студентот од листата.</p>
          <FacultyDistance path={`/dorms/${dorm.id}`} selected={faculty} onSelect={setFaculty} />
        </aside>
      </div>
    </div>
  )
}
