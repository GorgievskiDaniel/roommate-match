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
  skopje: boolean
  latitude: number
  longitude: number
}

export interface DormOption extends Option {
  roomOptions: Option[]
}

export interface Reference {
  dorms: DormOption[]
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
  dormRoomOptionId: number | null
  budgetMin: number | null
  budgetMax: number | null
  areas: string[]
  propertyTypes: PropertyType[]
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

export type PropertyType = 'APARTMENT' | 'HOUSE'

export interface ProfileSummary {
  userId: number
  firstName: string
  gender: Gender
  age: number
  faculty: string
  bio: string
  smoker: boolean
  hasPets: boolean
  guestsFrequency: GuestsFrequency
  tidiness: number
  sleepSchedule: SleepSchedule
  quietNeed: number
  hobbies: string[]
}

export interface ListingRequest {
  propertyType: PropertyType
  address: string
  area: string
  latitude: number
  longitude: number
  monthlyRent: number
  freeSpots: number
  description: string
}

export interface Listing extends ListingRequest {
  id: number
  ownerId: number
  areaName: string
  inviteCode: string | null
  images: { id: number; url: string }[]
  residents: ProfileSummary[]
}

export interface MapListing {
  id: number
  propertyType: PropertyType
  latitude: number
  longitude: number
  areaName: string
  monthlyRent: number
  freeSpots: number
  coverImageUrl: string
}

export interface MapDorm {
  id: number
  name: string
  latitude: number | null
  longitude: number | null
  seekers: number
  blocks: MapBlock[]
}

export interface MapBlock {
  name: string
  latitude: number
  longitude: number
  roomOption: string | null
  seekers: number
}

export interface DormDetail {
  id: number
  name: string
  seekers: { profile: ProfileSummary; roomOption: string | null }[]
}

export interface Match {
  profile: ProfileSummary
  score: {
    total: number
    habits: number
    hobbies: number
    rent: number | null
    location: number | null
    differentRoomType: boolean
  }
  situation: Situation | null
  roomOption: string | null
  areas: string[]
  listingId: number | null
}

export type WantedPlace = PropertyType | 'BOTH' | 'DORM'

/** The single questionnaire answer behind a housing type and its property types. */
export function wantedPlace(housingType: HousingType | null, propertyTypes: PropertyType[]): WantedPlace | null {
  if (housingType === 'DORM') return 'DORM'
  if (housingType !== 'APARTMENT' || propertyTypes.length === 0) return null
  return propertyTypes.length > 1 ? 'BOTH' : propertyTypes[0]
}

export const LABELS = {
  propertyType: { APARTMENT: 'Стан', HOUSE: 'Куќа' },
  gender: { MALE: 'Машки', FEMALE: 'Женски' },
  wantedPlace: { APARTMENT: 'Стан', HOUSE: 'Куќа', BOTH: 'Стан или куќа', DORM: 'Студентски дом' },
  situation: {
    HAS_PLACE: 'Имам стан и барам цимер',
    NEEDS_PLACE: 'Немам стан, барам некој што има',
    SEARCH_TOGETHER: 'Немам стан, бараме заедно',
  },
  guestsFrequency: { RARELY: 'Ретко', SOMETIMES: 'Понекогаш', OFTEN: 'Често' },
  sleepSchedule: { EARLY: 'Рано легнувам', FLEXIBLE: 'Променливо', LATE: 'Доцна легнувам' },
  genderPreference: { SAME: 'Само ист пол', ANY: 'Без разлика' },
} as const
