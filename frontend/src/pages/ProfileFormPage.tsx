import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api'
import { useAuth } from '../auth'
import BirthDatePicker from '../components/BirthDatePicker'
import FacultyPicker from '../components/FacultyPicker'
import { LABELS, wantedPlace, type Profile, type ProfileRequest, type Reference, type Situation, type WantedPlace } from '../types'

type Form = { [K in keyof ProfileRequest]: ProfileRequest[K] | null } & { budgetMinText: string; budgetMaxText: string }
type Errors = Record<string, string>

const EMPTY: Form = {
  firstName: '', gender: null, birthDate: '', faculty: '', bio: '',
  housingType: null, situation: null, dormId: null, dormRoomOptionId: null, budgetMin: null, budgetMax: null,
  budgetMinText: '', budgetMaxText: '', areas: [], propertyTypes: [],
  smoker: null, hasPets: null, guestsFrequency: null, cleaning: null, sleepSchedule: null, sound: null,
  communication: null, sharing: null, parties: null, partnerStays: null,
  acceptsSmoker: null, acceptsPets: null, roommateGenderPreference: null, hobbyIds: [],
}

const STEPS = ['За тебе', 'Што бараш', 'Навики', 'Однос со цимерот', 'Цимер и хобиња']

const STEP_OF_FIELD: Record<string, number> = {
  firstName: 0, gender: 0, birthDate: 0, faculty: 0, bio: 0,
  housingType: 1, propertyTypes: 1, situation: 1, dormId: 1, dormRoomOptionId: 1, budgetMin: 1, budgetMax: 1, areas: 1,
  smoker: 2, hasPets: 2, guestsFrequency: 2, cleaning: 2, sleepSchedule: 2, sound: 2,
  communication: 3, sharing: 3, parties: 3, partnerStays: 3,
  acceptsSmoker: 4, acceptsPets: 4, roommateGenderPreference: 4, hobbyIds: 4,
}

function validateStep(step: number, f: Form, reference: Reference): Errors {
  const e: Errors = {}
  const need = (key: keyof Form, message: string) => {
    if (f[key] === null || f[key] === '') e[key] = message
  }
  if (step === 0) {
    need('firstName', 'Внесете име.')
    need('gender', 'Изберете пол.')
    need('birthDate', 'Внесете датум на раѓање.')
    need('faculty', 'Внесете факултет.')
  }
  if (step === 1) {
    need('housingType', 'Изберете каде барате цимер.')
    if (f.housingType === 'DORM') {
      need('dormId', 'Изберете студентски дом.')
      if (reference.dorms.find((d) => d.id === f.dormId)?.roomOptions.length) {
        need('dormRoomOptionId', 'Изберете тип на соба.')
      }
    }
    if (f.housingType === 'APARTMENT') {
      need('situation', 'Изберете ја вашата положба.')
    }
    // Someone who has a place lists it, so the rent and the location come from the listing.
    if (f.housingType === 'APARTMENT' && f.situation !== 'HAS_PLACE') {
      if (f.budgetMinText === '') e.budgetMin = 'Внесете најмал буџет.'
      if (f.budgetMaxText === '') e.budgetMax = 'Внесете најголем буџет.'
      if (f.budgetMinText !== '' && f.budgetMaxText !== '' && Number(f.budgetMinText) > Number(f.budgetMaxText)) {
        e.budgetMax = 'Најголемиот буџет не може да е помал од најмалиот.'
      }
      if (!f.areas?.length) e.areas = 'Изберете барем една локација.'
    }
  }
  // Dorms allow neither smoking nor pets, so those questions are only asked for a flat or house.
  const apartment = f.housingType === 'APARTMENT'
  if (step === 2) {
    if (apartment) {
      need('smoker', 'Одговорете дали пушите.')
      need('hasPets', 'Одговорете дали имате миленичиња.')
    }
    need('guestsFrequency', 'Изберете колку често викате гости.')
    need('cleaning', 'Изберете како би го организирале чистењето.')
    need('sleepSchedule', 'Изберете распоред на спиење.')
    need('sound', 'Изберете за музиката и звукот во станот.')
  }
  if (step === 3) {
    need('communication', 'Изберете колку сакате да комуницирате со цимерот.')
    need('sharing', 'Изберете како замислувате делење на работите.')
    need('parties', 'Изберете што мислите за забави дома.')
    need('partnerStays', 'Изберете дали партнер може да преноќува.')
  }
  if (step === 4) {
    if (apartment) {
      need('acceptsSmoker', 'Одговорете дали прифаќате цимер пушач.')
      need('acceptsPets', 'Одговорете дали прифаќате цимер со миленичиња.')
    }
    need('roommateGenderPreference', 'Изберете пол на цимерот.')
    if (!f.hobbyIds?.length) e.hobbyIds = 'Изберете барем едно хоби.'
  }
  return e
}

function Choice<T extends string | number | boolean>(props: {
  legend: string
  value: T | null
  options: { value: T; label: string }[]
  onChange: (value: T) => void
  error?: string
  name: string
}) {
  return (
    <fieldset className="field" aria-invalid={Boolean(props.error)}>
      <legend>{props.legend}</legend>
      <div className="choices">
        {props.options.map((option) => (
          <label key={String(option.value)} className={props.value === option.value ? 'choice selected' : 'choice'}>
            <input
              type="radio"
              name={props.name}
              checked={props.value === option.value}
              onChange={() => props.onChange(option.value)}
            />
            {option.label}
          </label>
        ))}
      </div>
      {props.error && <small className="field-error">{props.error}</small>}
    </fieldset>
  )
}

/**
 * "I have a place" stands alone; the two ways of looking without one can be ticked together, which is
 * saved as NEEDS_OR_TOGETHER.
 */
function SituationChoice(props: { value: Situation | null; onChange: (value: Situation | null) => void; error?: string }) {
  const needs = props.value === 'NEEDS_PLACE' || props.value === 'NEEDS_OR_TOGETHER'
  const together = props.value === 'SEARCH_TOGETHER' || props.value === 'NEEDS_OR_TOGETHER'
  const combine = (n: boolean, t: boolean): Situation | null =>
    n && t ? 'NEEDS_OR_TOGETHER' : n ? 'NEEDS_PLACE' : t ? 'SEARCH_TOGETHER' : null
  const options: { key: string; label: string; checked: boolean; toggle: () => void }[] = [
    { key: 'HAS_PLACE', label: LABELS.situation.HAS_PLACE, checked: props.value === 'HAS_PLACE',
      toggle: () => props.onChange(props.value === 'HAS_PLACE' ? null : 'HAS_PLACE') },
    { key: 'NEEDS_PLACE', label: LABELS.situation.NEEDS_PLACE, checked: needs,
      toggle: () => props.onChange(combine(!needs, props.value === 'HAS_PLACE' ? false : together)) },
    { key: 'SEARCH_TOGETHER', label: LABELS.situation.SEARCH_TOGETHER, checked: together,
      toggle: () => props.onChange(combine(props.value === 'HAS_PLACE' ? false : needs, !together)) },
  ]
  return (
    <fieldset className="field" aria-invalid={Boolean(props.error)}>
      <legend>Која е твојата положба?</legend>
      <div className="choices">
        {options.map((option) => (
          <label key={option.key} className={option.checked ? 'choice selected' : 'choice'}>
            <input type="checkbox" checked={option.checked} onChange={option.toggle} />
            {option.label}
          </label>
        ))}
      </div>
      <small className="muted">Ако немаш стан, можеш да ги избереш двете можности.</small>
      {props.error && <small className="field-error">{props.error}</small>}
    </fieldset>
  )
}

function MultiChoice<T extends string | number>(props: {
  legend: string
  selected: T[]
  options: { value: T; label: string }[]
  onToggle: (value: T) => void
  error?: string
}) {
  return (
    <fieldset className="field" aria-invalid={Boolean(props.error)}>
      <legend>{props.legend}</legend>
      <div className="choices">
        {props.options.map((option) => {
          const checked = props.selected.includes(option.value)
          return (
            <label key={option.value} className={checked ? 'choice selected' : 'choice'}>
              <input type="checkbox" checked={checked} onChange={() => props.onToggle(option.value)} />
              {option.label}
            </label>
          )
        })}
      </div>
      {props.error && <small className="field-error">{props.error}</small>}
    </fieldset>
  )
}

function Text(props: { label: string; error?: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <label className="field">
      <span>{props.label}</span>
      {props.children}
      {props.hint && !props.error && <small className="muted">{props.hint}</small>}
      {props.error && <small className="field-error">{props.error}</small>}
    </label>
  )
}

const YES_NO = [{ value: true, label: 'Да' }, { value: false, label: 'Не' }]
const entries = <K extends string>(labels: Record<K, string>) =>
  (Object.keys(labels) as K[]).map((value) => ({ value, label: labels[value] }))

export default function ProfileFormPage({ editing = false }: { editing?: boolean }) {
  const { refresh } = useAuth()
  const navigate = useNavigate()
  const [reference, setReference] = useState<Reference | null>(null)
  const [form, setForm] = useState<Form>(EMPTY)
  const [step, setStep] = useState(0)
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let active = true
    Promise.all([api<Reference>('/reference'), editing ? api<Profile>('/profile/me') : Promise.resolve(null)])
      .then(([ref, profile]) => {
        if (!active) return
        setReference(ref)
        if (profile) {
          setForm({
            ...profile,
            areas: [...profile.areas],
            propertyTypes: [...profile.propertyTypes],
            hobbyIds: [...profile.hobbyIds],
            budgetMinText: profile.budgetMin?.toString() ?? '',
            budgetMaxText: profile.budgetMax?.toString() ?? '',
          })
        }
      })
      .catch((caught: Error) => active && setFormError(caught.message))
    return () => {
      active = false
    }
  }, [editing])

  function set<K extends keyof Form>(key: K, value: Form[K]) {
    setForm((current) => ({ ...current, [key]: value }))
    setErrors(({ [key]: _removed, ...rest }) => rest)
  }

  function chooseWantedPlace(value: WantedPlace) {
    set('housingType', value === 'DORM' ? 'DORM' : 'APARTMENT')
    set('propertyTypes', value === 'DORM' ? [] : value === 'BOTH' ? ['APARTMENT', 'HOUSE'] : [value])
  }

  function toggle<T>(list: T[], value: T): T[] {
    return list.includes(value) ? list.filter((item) => item !== value) : [...list, value]
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setFormError(null)
    const stepErrors = validateStep(step, form, reference!)
    if (Object.keys(stepErrors).length > 0) {
      setErrors(stepErrors)
      return
    }
    if (step < STEPS.length - 1) {
      setStep(step + 1)
      window.scrollTo(0, 0)
      return
    }
    const apartment = form.housingType === 'APARTMENT'
    const searching = apartment && form.situation !== 'HAS_PLACE'
    const { budgetMinText, budgetMaxText, ...rest } = form
    setSubmitting(true)
    try {
      await api('/profile/me', {
        method: 'PUT',
        body: {
          ...rest,
          situation: apartment ? form.situation : null,
          dormId: apartment ? null : form.dormId,
          dormRoomOptionId: apartment ? null : form.dormRoomOptionId,
          budgetMin: searching ? Number(budgetMinText) : null,
          budgetMax: searching ? Number(budgetMaxText) : null,
          areas: searching ? form.areas : [],
          propertyTypes: apartment ? form.propertyTypes : [],
          smoker: apartment ? form.smoker : null,
          hasPets: apartment ? form.hasPets : null,
          acceptsSmoker: apartment ? form.acceptsSmoker : null,
          acceptsPets: apartment ? form.acceptsPets : null,
        },
      })
      await refresh()
      navigate(editing ? '/profile' : '/')
    } catch (caught) {
      setSubmitting(false)
      if (caught instanceof ApiError) {
        setFormError(caught.message)
        setErrors(caught.fieldErrors)
        const failed = Object.keys(caught.fieldErrors).map((field) => STEP_OF_FIELD[field] ?? step)
        if (failed.length > 0) setStep(Math.min(...failed))
      } else {
        setFormError('Настана грешка. Обидете се повторно.')
      }
    }
  }

  if (!reference) {
    return formError
      ? <p role="alert" className="form-error">{formError}</p>
      : <p className="muted">Се вчитува…</p>
  }

  const roomOptions = reference.dorms.find((d) => d.id === form.dormId)?.roomOptions ?? []

  return (
    <form className="card questionnaire" onSubmit={submit} noValidate>
      <p className="eyebrow">Чекор {step + 1} од {STEPS.length}</p>
      <h1>{editing ? `Измени: ${STEPS[step]}` : STEPS[step]}</h1>
      <div className="progress" aria-hidden="true">
        {STEPS.map((name, index) => <span key={name} className={index <= step ? 'on' : ''} />)}
      </div>
      {formError && <p role="alert" className="form-error">{formError}</p>}

      {step === 0 && (
        <>
          <Text label="Име" error={errors.firstName}>
            <input value={form.firstName ?? ''} maxLength={60} autoComplete="given-name"
              onChange={(e) => set('firstName', e.target.value)} />
          </Text>
          <Choice name="gender" legend="Пол" value={form.gender} options={entries(LABELS.gender)}
            onChange={(v) => set('gender', v)} error={errors.gender} />
          <BirthDatePicker value={form.birthDate ?? ''} onChange={(v) => set('birthDate', v)} error={errors.birthDate} />
          <FacultyPicker faculties={reference.faculties ?? []} value={form.faculty ?? ''}
            onChange={(v) => set('faculty', v)} error={errors.faculty} />
          <Text label="Краток опис (незадолжително)" error={errors.bio} hint={`${(form.bio ?? '').length}/500`}>
            <textarea rows={4} maxLength={500} value={form.bio ?? ''} onChange={(e) => set('bio', e.target.value)} />
          </Text>
        </>
      )}

      {step === 1 && (
        <>
          <Choice name="wantedPlace" legend="Каде бараш цимер?" value={wantedPlace(form.housingType, form.propertyTypes ?? [])}
            options={entries(LABELS.wantedPlace)} onChange={chooseWantedPlace}
            error={errors.housingType ?? errors.propertyTypes} />
          {form.housingType === 'DORM' && (
            <Choice name="dormId" legend="Во кој дом?" value={form.dormId}
              options={reference.dorms.map((d) => ({ value: d.id, label: d.openingNote ? `${d.name} (уште не е отворен)` : d.name }))}
              onChange={(v) => { set('dormId', v); set('dormRoomOptionId', null) }} error={errors.dormId} />
          )}
          {form.housingType === 'DORM' && roomOptions.length > 0 && (
            <Choice name="dormRoomOptionId" legend="Каков тип на соба?" value={form.dormRoomOptionId}
              options={roomOptions.map((o) => ({ value: o.id, label: o.name }))}
              onChange={(v) => set('dormRoomOptionId', v)} error={errors.dormRoomOptionId} />
          )}
          {form.housingType === 'APARTMENT' && (
            <>
              <SituationChoice value={form.situation} onChange={(v) => set('situation', v)} error={errors.situation} />
              {form.situation === 'HAS_PLACE' ? (
                <p className="hint-box">
                  Кирија и локација не треба да внесуваш: по профилот објави го станот во „Мој оглас“, а совпаѓањата
                  се прават според киријата и адресата од огласот.
                </p>
              ) : (
              <>
              <div className="field-row">
                <Text label="Кирија од (€)" error={errors.budgetMin}>
                  <input type="number" min={0} inputMode="numeric" value={form.budgetMinText}
                    onChange={(e) => { set('budgetMinText', e.target.value); set('budgetMin', null) }} />
                </Text>
                <Text label="Кирија до (€)" error={errors.budgetMax}>
                  <input type="number" min={0} inputMode="numeric" value={form.budgetMaxText}
                    onChange={(e) => { set('budgetMaxText', e.target.value); set('budgetMax', null) }} />
                </Text>
              </div>
              <MultiChoice legend="Каде бараш? Општини во Скопје" selected={form.areas ?? []}
                options={reference.areas.filter((a) => a.skopje).map((a) => ({ value: a.code, label: a.name }))}
                onToggle={(v) => set('areas', toggle(form.areas ?? [], v))} />
              <MultiChoice legend="Други градови" selected={form.areas ?? []}
                options={reference.areas.filter((a) => !a.skopje).map((a) => ({ value: a.code, label: a.name }))}
                onToggle={(v) => set('areas', toggle(form.areas ?? [], v))} error={errors.areas} />
              </>
              )}
            </>
          )}
        </>
      )}

      {step === 2 && (
        <>
          {form.housingType === 'DORM' ? (
            <p className="notice">Во студентските домови пушењето и миленичињата не се дозволени, па не те прашуваме за нив.</p>
          ) : (
            <>
              <Choice name="smoker" legend="Дали пушиш?" value={form.smoker} options={YES_NO}
                onChange={(v) => set('smoker', v)} error={errors.smoker} />
              <Choice name="hasPets" legend="Дали имаш миленичиња?" value={form.hasPets} options={YES_NO}
                onChange={(v) => set('hasPets', v)} error={errors.hasPets} />
            </>
          )}
          <Choice name="guestsFrequency" legend="Колку често викаш гости?" value={form.guestsFrequency}
            options={entries(LABELS.guestsFrequency)} onChange={(v) => set('guestsFrequency', v)}
            error={errors.guestsFrequency} />
          <Choice name="cleaning" legend="Како би го организирале чистењето?" value={form.cleaning}
            options={entries(LABELS.cleaning)} onChange={(v) => set('cleaning', v)} error={errors.cleaning} />
          <Choice name="sleepSchedule" legend="Кога легнуваш?" value={form.sleepSchedule}
            options={entries(LABELS.sleepSchedule)} onChange={(v) => set('sleepSchedule', v)}
            error={errors.sleepSchedule} />
          <Choice name="sound" legend="Музика и звук во станот?" value={form.sound}
            options={entries(LABELS.sound)} onChange={(v) => set('sound', v)} error={errors.sound} />
        </>
      )}

      {step === 3 && (
        <>
          <Choice name="communication" legend="Колку сакаш да комуницираш со цимерот?" value={form.communication}
            options={entries(LABELS.communication)} onChange={(v) => set('communication', v)} error={errors.communication} />
          <Choice name="sharing" legend="Како замислуваш делење на работите?" value={form.sharing}
            options={entries(LABELS.sharing)} onChange={(v) => set('sharing', v)} error={errors.sharing} />
          <Choice name="parties" legend="Што мислиш за забави дома?" value={form.parties}
            options={entries(LABELS.parties)} onChange={(v) => set('parties', v)} error={errors.parties} />
          <Choice name="partnerStays" legend="Партнер да преноќува?" value={form.partnerStays}
            options={entries(LABELS.partnerStays)} onChange={(v) => set('partnerStays', v)} error={errors.partnerStays} />
        </>
      )}

      {step === 4 && (
        <>
          {form.housingType !== 'DORM' && (
            <>
              <Choice name="acceptsSmoker" legend="Прифаќаш цимер пушач?" value={form.acceptsSmoker} options={YES_NO}
                onChange={(v) => set('acceptsSmoker', v)} error={errors.acceptsSmoker} />
              <Choice name="acceptsPets" legend="Прифаќаш цимер со миленичиња?" value={form.acceptsPets} options={YES_NO}
                onChange={(v) => set('acceptsPets', v)} error={errors.acceptsPets} />
            </>
          )}
          <Choice name="genderPreference" legend="Пол на цимерот" value={form.roommateGenderPreference}
            options={entries(LABELS.genderPreference)} onChange={(v) => set('roommateGenderPreference', v)}
            error={errors.roommateGenderPreference} />
          <MultiChoice legend="Твоите хобиња" selected={form.hobbyIds ?? []}
            options={reference.hobbies.map((h) => ({ value: h.id, label: h.name }))}
            onToggle={(v) => set('hobbyIds', toggle(form.hobbyIds ?? [], v))} error={errors.hobbyIds} />
        </>
      )}

      <div className="form-actions">
        {step > 0 && (
          <button type="button" className="button secondary" onClick={() => setStep(step - 1)}>Назад</button>
        )}
        <button type="submit" className="button" disabled={submitting}>
          {step < STEPS.length - 1 ? 'Понатаму' : 'Зачувај го профилот'}
        </button>
      </div>
    </form>
  )
}
