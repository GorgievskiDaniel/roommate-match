import { useEffect, useId, useState } from 'react'

const MONTHS = ['јануари', 'февруари', 'март', 'април', 'мај', 'јуни', 'јули', 'август', 'септември', 'октомври',
  'ноември', 'декември']
const YOUNGEST = 17
const OLDEST = 70

function daysIn(month: number, year: number): number {
  // Without a year yet, 29 February stays possible.
  return new Date(year || 2000, month, 0).getDate()
}

function parts(value: string) {
  const [year, month, day] = value ? value.split('-').map(Number) : [0, 0, 0]
  return { day: day || 0, month: month || 0, year: year || 0 }
}

/**
 * Day, month and year in that order, whatever language the browser uses. The browser's own date field
 * follows the browser language, which on many computers means month first.
 */
export default function BirthDatePicker(props: { value: string; onChange: (value: string) => void; error?: string }) {
  const id = useId()
  const [date, setDate] = useState(() => parts(props.value))
  // A profile loaded after the first render still shows its date.
  useEffect(() => {
    if (props.value) setDate(parts(props.value))
  }, [props.value])
  const thisYear = new Date().getFullYear()
  const years = Array.from({ length: OLDEST - YOUNGEST + 1 }, (_, i) => thisYear - YOUNGEST - i)
  const days = Array.from({ length: date.month ? daysIn(date.month, date.year) : 31 }, (_, i) => i + 1)

  function update(change: Partial<typeof date>) {
    const next = { ...date, ...change }
    // A day that does not exist in the new month, such as 31 April, is cleared.
    if (next.day && next.month && next.day > daysIn(next.month, next.year)) next.day = 0
    setDate(next)
    const complete = next.day && next.month && next.year
    props.onChange(complete
      ? `${next.year}-${String(next.month).padStart(2, '0')}-${String(next.day).padStart(2, '0')}`
      : '')
  }

  return (
    <fieldset className="field" aria-invalid={Boolean(props.error)}>
      <legend>Датум на раѓање</legend>
      <div className="date-parts">
        <label>
          <span className="sr-only">Ден</span>
          <select id={`${id}-day`} value={date.day} onChange={(e) => update({ day: Number(e.target.value) })}>
            <option value={0}>Ден</option>
            {days.map((day) => <option key={day} value={day}>{day}</option>)}
          </select>
        </label>
        <label>
          <span className="sr-only">Месец</span>
          <select value={date.month} onChange={(e) => update({ month: Number(e.target.value) })}>
            <option value={0}>Месец</option>
            {MONTHS.map((name, index) => <option key={name} value={index + 1}>{name}</option>)}
          </select>
        </label>
        <label>
          <span className="sr-only">Година</span>
          <select value={date.year} onChange={(e) => update({ year: Number(e.target.value) })}>
            <option value={0}>Година</option>
            {years.map((year) => <option key={year} value={year}>{year}</option>)}
          </select>
        </label>
      </div>
      {props.error && <small className="field-error">{props.error}</small>}
    </fieldset>
  )
}
