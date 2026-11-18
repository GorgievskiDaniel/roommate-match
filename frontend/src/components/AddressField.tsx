import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { api } from '../api'

export interface AddressSuggestion {
  label: string
  latitude: number
  longitude: number
}

/** The address of the listing, with suggestions from OpenStreetMap while typing. */
export default function AddressField(props: {
  value: string
  onChange: (value: string) => void
  onPick: (suggestion: AddressSuggestion) => void
  error?: string
}) {
  const id = useId()
  const [suggestions, setSuggestions] = useState<AddressSuggestion[]>([])
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const [searching, setSearching] = useState(false)
  // Only what the person typed is searched, not an address they just picked.
  const typed = useRef(false)

  useEffect(() => {
    if (!typed.current) return
    const query = props.value.trim()
    if (query.length < 3) {
      setSuggestions([])
      return
    }
    let current = true
    // Waits for a short pause in typing, so the shared search service gets one request, not one per letter.
    const timer = setTimeout(() => {
      setSearching(true)
      api<AddressSuggestion[]>(`/addresses?q=${encodeURIComponent(query)}`)
        .then((found) => {
          if (!current) return
          setSuggestions(found)
          setActive(0)
          setOpen(true)
        })
        .catch(() => current && setSuggestions([]))
        .finally(() => current && setSearching(false))
    }, 350)
    return () => {
      current = false
      clearTimeout(timer)
    }
  }, [props.value])

  function pick(suggestion: AddressSuggestion) {
    typed.current = false
    props.onChange(suggestion.label)
    props.onPick(suggestion)
    setOpen(false)
    setSuggestions([])
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (!open || suggestions.length === 0) return
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      const step = event.key === 'ArrowDown' ? 1 : -1
      setActive((current) => (current + step + suggestions.length) % suggestions.length)
    } else if (event.key === 'Enter') {
      event.preventDefault()
      pick(suggestions[active])
    } else if (event.key === 'Escape') {
      setOpen(false)
    }
  }

  const listId = `${id}-list`
  const showList = open && suggestions.length > 0
  return (
    <div className="field combo">
      <label htmlFor={id}>Адреса</label>
      <input
        id={id}
        role="combobox"
        aria-expanded={showList}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={showList ? `${id}-${active}` : undefined}
        aria-invalid={Boolean(props.error)}
        autoComplete="off"
        maxLength={200}
        placeholder="Почни да пишуваш, на пр. Партизански одреди 15"
        value={props.value}
        onChange={(event) => {
          typed.current = true
          props.onChange(event.target.value)
        }}
        onFocus={() => suggestions.length > 0 && setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={onKeyDown}
      />
      {showList && (
        <ul id={listId} role="listbox" className="combo-list" aria-label="Предлози за адреса">
          {suggestions.map((suggestion, index) => (
            <li
              key={`${suggestion.label}-${index}`}
              id={`${id}-${index}`}
              role="option"
              aria-selected={index === active}
              className={index === active ? 'active' : undefined}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => pick(suggestion)}
              onMouseEnter={() => setActive(index)}
            >
              <span>{suggestion.label}</span>
            </li>
          ))}
        </ul>
      )}
      {props.error
        ? <small className="field-error">{props.error}</small>
        : <small className="muted">
            {searching ? 'Се бара адресата…' : 'Избери ја адресата од предлозите, за да се означи на мапата. Адресата и точната локација се видливи за сите најавени корисници.'}
          </small>}
    </div>
  )
}
