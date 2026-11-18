import type { Gender } from '../types'

/** The same two figures as on the sign-in page, used until a student adds a picture of their own. */
function Drawing({ gender }: { gender: Gender }) {
  if (gender === 'FEMALE') {
    return (
      <svg viewBox="10 14 180 180" aria-hidden="true">
        <rect x="10" y="14" width="180" height="180" fill="#f0be3c" />
        <rect x="48" y="26" width="104" height="150" rx="52" fill="#16241d" />
        <rect x="86" y="96" width="28" height="50" fill="#f7f2e6" />
        <path d="M20 300 V210 A80 80 0 0 1 180 210 V300 Z" fill="#1f4d3a" />
        <path d="M78 132 Q100 158 122 132 Z" fill="#f7f2e6" />
        <circle cx="100" cy="76" r="40" fill="#f7f2e6" />
        <path d="M60 76 A40 40 0 0 1 140 76 C120 62 104 44 60 76 Z" fill="#16241d" />
      </svg>
    )
  }
  return (
    <svg viewBox="10 14 180 180" aria-hidden="true">
      <rect x="10" y="14" width="180" height="180" fill="#1f4d3a" />
      <rect x="86" y="96" width="28" height="50" fill="#f7f2e6" />
      <path d="M20 300 V210 A80 80 0 0 1 180 210 V300 Z" fill="#f0be3c" />
      <path d="M78 132 Q100 158 122 132 Z" fill="#f7f2e6" />
      <circle cx="100" cy="72" r="42" fill="#f7f2e6" />
      <path d="M56 74 A44 44 0 0 1 144 74 C130 50 92 44 56 74 Z" fill="#16241d" />
    </svg>
  )
}

export default function Avatar(props: { url: string | null | undefined; gender: Gender; name: string; size?: number }) {
  const size = props.size ?? 48
  return (
    <span className="avatar" style={{ width: size, height: size }}>
      {props.url ? <img src={props.url} alt={`Слика на ${props.name}`} /> : <Drawing gender={props.gender} />}
    </span>
  )
}
