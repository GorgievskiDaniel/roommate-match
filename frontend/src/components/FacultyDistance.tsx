import { useEffect, useState } from 'react'
import { api } from '../api'

export interface Faculty {
  id: number
  name: string
  latitude: number
  longitude: number
}

interface Trip {
  kilometers: number
  minutes: number
}

interface Distance {
  walking: Trip | null
  driving: Trip
}

/** `path` is the API path of the place, for example /dorms/3 or /listings/5. */
export default function FacultyDistance(props: {
  path: string
  selected: Faculty | null
  onSelect: (faculty: Faculty | null) => void
}) {
  const { path, selected, onSelect } = props
  const [faculties, setFaculties] = useState<Faculty[]>([])
  const [distance, setDistance] = useState<Distance | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const facultyId = selected?.id ?? null

  useEffect(() => {
    let active = true
    api<Faculty[]>('/faculties')
      .then((loaded) => active && setFaculties(loaded))
      .catch((caught: Error) => active && setError(caught.message))
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    setDistance(null)
    setError(null)
    setLoading(false)
    if (facultyId === null) return
    let active = true
    setLoading(true)
    api<Distance>(`${path}/distance?facultyId=${facultyId}`)
      .then((loaded) => active && setDistance(loaded))
      .catch((caught: Error) => active && setError(caught.message))
      .finally(() => active && setLoading(false))
    return () => {
      active = false
    }
  }, [path, facultyId])

  return (
    <section className="faculty-distance">
      <label className="field">
        <span>Колку е далеку до факултет?</span>
        <select
          value={facultyId ?? ''}
          onChange={(event) => onSelect(faculties.find((f) => f.id === Number(event.target.value)) ?? null)}
        >
          <option value="">Избери факултет</option>
          {faculties.map((faculty) => <option key={faculty.id} value={faculty.id}>{faculty.name}</option>)}
        </select>
      </label>
      {loading && <p className="muted">Се пресметува…</p>}
      {error && <p role="alert" className="field-error">{error}</p>}
      {distance && (
        <ul className="distance-result" aria-live="polite">
          {distance.walking && <li>Пеш: <strong>{distance.walking.kilometers} km</strong>, околу {distance.walking.minutes} мин</li>}
          <li>Со кола: <strong>{distance.driving.kilometers} km</strong>, околу {distance.driving.minutes} мин</li>
        </ul>
      )}
    </section>
  )
}
