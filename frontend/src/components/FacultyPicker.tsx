import { useId, useMemo, useState, type KeyboardEvent } from 'react'
import type { FacultyOption } from '../types'

const LATIN_PAIRS: [string, string][] = [
  ['sh', 'ш'], ['ch', 'ч'], ['zh', 'ж'], ['kj', 'ќ'], ['gj', 'ѓ'], ['lj', 'љ'], ['nj', 'њ'], ['dz', 'ѕ'], ['dj', 'џ'],
]
const LATIN_LETTERS: Record<string, string> = {
  a: 'а', b: 'б', v: 'в', g: 'г', d: 'д', e: 'е', z: 'з', i: 'и', j: 'ј', k: 'к', l: 'л', m: 'м', n: 'н',
  o: 'о', p: 'п', r: 'р', s: 'с', t: 'т', u: 'у', f: 'ф', h: 'х', c: 'ц', č: 'ч', š: 'ш', ž: 'ж', ć: 'ќ', y: 'ј',
}

/** Lower case, without quotes and punctuation, so "„Блаже Конески“" and "блаже конески" are the same. */
function normalize(text: string): string {
  return text.toLowerCase().replace(/[„“”"'’.,()\-–]/g, ' ').replace(/\s+/g, ' ').trim()
}

/** So a student typing "finki" or "fakultet za informaticki" still finds the faculty. */
function toCyrillic(text: string): string {
  let result = text
  for (const [latin, cyrillic] of LATIN_PAIRS) result = result.replaceAll(latin, cyrillic)
  return [...result].map((char) => LATIN_LETTERS[char] ?? char).join('')
}

interface Indexed {
  option: FacultyOption
  words: string[]
  abbreviations: string[]
}

function index(option: FacultyOption): Indexed {
  const abbreviations = [option.shortName, option.latin].filter((value): value is string => Boolean(value)).map(normalize)
  return {
    option,
    abbreviations,
    words: normalize(`${option.label} ${option.university} ${abbreviations.join(' ')}`).split(' '),
  }
}

/** Every word typed must start some word of the faculty, in any order: "компјутерско инженерство" finds ФИНКИ. */
function search(entries: Indexed[], query: string): FacultyOption[] {
  const typed = normalize(query)
  if (!typed) return entries.map((entry) => entry.option)
  const variants = [...new Set([typed, toCyrillic(typed)])].map((text) => text.split(' '))
  const scored = entries.flatMap((entry) => {
    const fits = variants.some((tokens) => tokens.every((token) => entry.words.some((word) => word.startsWith(token))))
    if (!fits) return []
    // An exact abbreviation such as "финки" comes first.
    const exact = variants.some((tokens) => tokens.length === 1 && entry.abbreviations.includes(tokens[0]))
    return [{ option: entry.option, rank: exact ? 0 : 1 }]
  })
  return scored.sort((a, b) => a.rank - b.rank).map((entry) => entry.option)
}

export default function FacultyPicker(props: {
  faculties: FacultyOption[]
  value: string
  onChange: (value: string) => void
  error?: string
}) {
  const id = useId()
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const indexed = useMemo(() => props.faculties.map(index), [props.faculties])
  const chosen = props.faculties.some((faculty) => faculty.label === props.value)
  // Once a faculty is chosen, opening the list again shows all of them instead of just that one.
  const results = useMemo(() => search(indexed, chosen ? '' : props.value), [indexed, chosen, props.value])

  function choose(option: FacultyOption) {
    props.onChange(option.label)
    setOpen(false)
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      if (!open) {
        setOpen(true)
        return
      }
      const step = event.key === 'ArrowDown' ? 1 : -1
      setActive((current) => (results.length ? (current + step + results.length) % results.length : 0))
    } else if (event.key === 'Enter' && open && results[active]) {
      // Picks the highlighted faculty instead of submitting the form.
      event.preventDefault()
      choose(results[active])
    } else if (event.key === 'Escape') {
      setOpen(false)
    }
  }

  const listId = `${id}-list`
  return (
    <div className="field combo">
      <label htmlFor={id}>Факултет</label>
      <input
        id={id}
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={open && results[active] ? `${id}-${active}` : undefined}
        aria-invalid={Boolean(props.error)}
        autoComplete="off"
        maxLength={120}
        placeholder="Почни да пишуваш, на пр. ФИНКИ"
        value={props.value}
        onChange={(event) => {
          props.onChange(event.target.value)
          setActive(0)
          setOpen(true)
        }}
        onFocus={() => setOpen(true)}
        onClick={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={onKeyDown}
      />
      {open && (
        <ul id={listId} role="listbox" className="combo-list" aria-label="Факултети">
          {results.map((option, index) => (
            <li
              key={option.label}
              id={`${id}-${index}`}
              role="option"
              aria-selected={index === active}
              className={index === active ? 'active' : undefined}
              // Keeps the focus in the field, so the blur does not close the list before the click lands.
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => choose(option)}
              onMouseEnter={() => setActive(index)}
            >
              <span>{option.label}</span>
              <small>{option.university}</small>
            </li>
          ))}
          {results.length === 0 && (
            <li className="combo-empty" aria-disabled="true">Го нема во листата. Ќе се зачува како што е напишан.</li>
          )}
        </ul>
      )}
      {props.error
        ? <small className="field-error">{props.error}</small>
        : <small className="muted">Избери од листата, или напиши го целото име ако го нема.</small>}
    </div>
  )
}
