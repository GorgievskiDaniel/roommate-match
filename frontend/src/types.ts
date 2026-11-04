export type Gender = 'MALE' | 'FEMALE'
export type HousingType = 'APARTMENT' | 'DORM'
export type Situation = 'HAS_PLACE' | 'NEEDS_PLACE' | 'SEARCH_TOGETHER'
export type GuestsFrequency = 'RARELY' | 'SOMETIMES' | 'OFTEN'
export type SleepSchedule = 'EARLY' | 'FLEXIBLE' | 'LATE'
export type GenderPreference = 'SAME' | 'ANY'

export interface CurrentUser {
  id: number
  email: string
  profileCompleted: boolean
}

export interface Option {
  id: number
  name: string
}

export interface AreaOption {
  code: string
  name: string
}

export interface Reference {
  dorms: Option[]
  hobbies: Option[]
  areas: AreaOption[]
}

export interface ProfileRequest {
  firstName: string
  gender: Gender
  birthDate: string
  faculty: string
  bio: string
  housingType: HousingType
  situation: Situation | null
  dormId: number | null
  budgetMin: number | null
  budgetMax: number | null
  areas: string[]
  smoker: boolean
  hasPets: boolean
  guestsFrequency: GuestsFrequency
  tidiness: number
  sleepSchedule: SleepSchedule
  quietNeed: number
  acceptsSmoker: boolean
  acceptsPets: boolean
  roommateGenderPreference: GenderPreference
  hobbyIds: number[]
}

export interface Profile extends ProfileRequest {
  age: number
}

export const LABELS = {
  gender: { MALE: 'Машки', FEMALE: 'Женски' },
  housingType: { APARTMENT: 'Стан или куќа', DORM: 'Студентски дом' },
  situation: {
    HAS_PLACE: 'Имам стан и барам цимер',
    NEEDS_PLACE: 'Немам стан, барам некој што има',
    SEARCH_TOGETHER: 'Немам стан, бараме заедно',
  },
  guestsFrequency: { RARELY: 'Ретко', SOMETIMES: 'Понекогаш', OFTEN: 'Често' },
  sleepSchedule: { EARLY: 'Рано легнувам', FLEXIBLE: 'Променливо', LATE: 'Доцна легнувам' },
  genderPreference: { SAME: 'Само ист пол', ANY: 'Без разлика' },
} as const
