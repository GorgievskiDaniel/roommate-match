export type Gender = 'MALE' | 'FEMALE'
export type HousingType = 'APARTMENT' | 'DORM'
export type Situation = 'HAS_PLACE' | 'NEEDS_PLACE' | 'SEARCH_TOGETHER' | 'NEEDS_OR_TOGETHER'
export type GuestsFrequency = 'RARELY' | 'SOMETIMES' | 'OFTEN'
export type SleepSchedule = 'EARLY' | 'FLEXIBLE' | 'LATE'
export type Cleaning = 'WHEN_POSSIBLE' | 'SCHEDULE' | 'REGULAR'
// NO_PREFERENCE: the student does not mind either way, so the answer never lowers a match.
export type Sound = 'HEADPHONES' | 'QUIET_SPEAKER' | 'LOUD' | 'NO_PREFERENCE'
export type Communication = 'MINIMAL' | 'OCCASIONAL' | 'TOGETHER' | 'NO_PREFERENCE'
export type Sharing = 'SEPARATE' | 'BASICS' | 'EVERYTHING' | 'NO_PREFERENCE'
export type Parties = 'NONE' | 'SMALL' | 'PARTIES'
export type PartnerStays = 'NO' | 'SOMETIMES' | 'YES'
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
  /** Set only for a dorm that is not in use yet. */
  openingNote: string | null
}

export type AmenityCode = 'PARKING' | 'BALCONY' | 'YARD' | 'WIFI' | 'AIR_CONDITIONING' | 'CENTRAL_HEATING'
  | 'WASHING_MACHINE' | 'DISHWASHER' | 'ELEVATOR'

export interface Amenity {
  code: AmenityCode
  label: string
}

/** In the same order as on the server. A yard belongs to a house, a lift to a block of flats. */
export const AMENITIES: (Amenity & { only?: PropertyType })[] = [
  { code: 'PARKING', label: 'Паркинг' },
  { code: 'BALCONY', label: 'Тераса' },
  { code: 'YARD', label: 'Двор', only: 'HOUSE' },
  { code: 'WIFI', label: 'WiFi' },
  { code: 'AIR_CONDITIONING', label: 'Клима' },
  { code: 'CENTRAL_HEATING', label: 'Централно греење' },
  { code: 'WASHING_MACHINE', label: 'Машина за перење' },
  { code: 'DISHWASHER', label: 'Машина за садови' },
  { code: 'ELEVATOR', label: 'Лифт', only: 'APARTMENT' },
]

export interface FacultyOption {
  /** What is saved on the profile. */
  label: string
  shortName: string | null
  latin: string | null
  university: string
  city: string
}

export interface Reference {
  dorms: DormOption[]
  hobbies: Option[]
  areas: AreaOption[]
  faculties: FacultyOption[]
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
  /** These four are null for a dorm, where smoking and pets are not allowed. */
  smoker: boolean | null
  hasPets: boolean | null
  guestsFrequency: GuestsFrequency
  cleaning: Cleaning
  sleepSchedule: SleepSchedule
  sound: Sound
  communication: Communication
  sharing: Sharing
  parties: Parties
  partnerStays: PartnerStays
  acceptsSmoker: boolean | null
  acceptsPets: boolean | null
  roommateGenderPreference: GenderPreference
  hobbyIds: number[]
}

export interface Profile extends ProfileRequest {
  age: number
  /** Null until the student adds a picture; a drawing by gender is shown instead. */
  avatarUrl: string | null
}

export type PropertyType = 'APARTMENT' | 'HOUSE'

export interface ProfileSummary {
  userId: number
  firstName: string
  gender: Gender
  age: number
  faculty: string
  bio: string
  /** Null for a dorm resident. */
  smoker: boolean | null
  hasPets: boolean | null
  guestsFrequency: GuestsFrequency
  cleaning: Cleaning
  sleepSchedule: SleepSchedule
  sound: Sound
  communication: Communication
  sharing: Sharing
  parties: Parties
  partnerStays: PartnerStays
  hobbies: string[]
  avatarUrl: string | null
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
  amenities: AmenityCode[]
}

export interface Listing extends Omit<ListingRequest, 'amenities'> {
  amenities: Amenity[]
  id: number
  ownerId: number
  areaName: string
  inviteCode: string | null
  images: { id: number; url: string }[]
  residents: ProfileSummary[]
  /** The gender the owner wants a roommate of; null when the owner does not mind. */
  roommateGender: Gender | null
}

/** Who may move in, as the owner answered in their profile. */
export function wantedRoommate(gender: Gender | null): string {
  if (gender === 'FEMALE') return 'Само девојки'
  if (gender === 'MALE') return 'Само момчиња'
  return 'Без разлика на полот'
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

/** One flat or house on the page of all listings. */
export interface BrowseListing {
  id: number
  propertyType: PropertyType
  area: string
  areaName: string
  address: string
  monthlyRent: number
  freeSpots: number
  /** What the place offers. */
  amenities: Amenity[]
  coverImageUrl: string
  imageCount: number
  residents: number
}

/** A dorm on the page of all listings: no rent or amenities, but photos and maybe a later opening. */
export interface BrowseDorm {
  id: number
  name: string
  latitude: number | null
  longitude: number | null
  seekers: number
  coverPhotoUrl: string | null
  photoCount: number
  openingNote: string | null
  /** The part of town people know the dorm by, such as Автокоманда, when it differs from the municipality. */
  neighbourhood: string | null
}

export interface MapDorm {
  id: number
  name: string
  latitude: number | null
  longitude: number | null
  seekers: number
  blocks: MapBlock[]
  coverPhotoUrl: string | null
  openingNote: string | null
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
  neighbourhood: string | null
  photoUrls: string[]
  /** Set only for a dorm that is not in use yet. */
  openingNote: string | null
  /** The post is the student's own ad, or null when they have not written one. */
  seekers: { profile: ProfileSummary; roomOption: string | null; post: string | null }[]
}

export interface MapSearchResult {
  understood: boolean
  filters: string[]
  listingIds: number[]
  dormIds: number[]
}

export interface Conversation {
  id: number
  otherUserId: number
  otherName: string
  lastMessage: string | null
  lastMessageAt: string | null
  unread: number
}

export interface ChatMessage {
  id: number
  conversationId: number
  senderId: number
  body: string
  createdAt: string
}

export interface Match {
  profile: ProfileSummary
  score: {
    total: number
    /** Guests, cleaning, sleep, sound, and for flats smoking and pets. */
    daily: number
    /** Communication, sharing, parties and partner staying over. */
    together: number
    /** Only for flats and houses; above the budget by more than 20 % there is no match at all. */
    rent: number | null
    hobbies: number
    age: number
    /** Wants another room type in the dorm; such matches come last. */
    differentRoomType: boolean
    /** Both study at the same faculty, which adds a little to the total. */
    sameFaculty: boolean
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
    NEEDS_OR_TOGETHER: 'Немам стан: барам некој што има, или бараме заедно',
  },
  guestsFrequency: { RARELY: 'Ретко', SOMETIMES: 'Понекогаш', OFTEN: 'Често' },
  sleepSchedule: { EARLY: 'Рано легнувам', FLEXIBLE: 'Променливо', LATE: 'Доцна легнувам' },
  genderPreference: { SAME: 'Само ист пол', ANY: 'Без разлика' },
  cleaning: { WHEN_POSSIBLE: 'Секој чисти по себе кога ќе стигне', SCHEDULE: 'Договорен распоред', REGULAR: 'Чистиме редовно и веднаш' },
  sound: { HEADPHONES: 'Само со слушалки', QUIET_SPEAKER: 'Тивко на звучник', LOUD: 'Гласно е во ред', NO_PREFERENCE: 'Не ми е важно' },
  communication: {
    MINIMAL: 'Преферирам минимална комуникација',
    OCCASIONAL: 'Повремена комуникација',
    TOGETHER: 'Сакам да поминуваме време заедно',
    NO_PREFERENCE: 'Не ми е важно',
  },
  sharing: {
    SEPARATE: 'Секој купува и користи свое',
    BASICS: 'Делиме основни работи (детергент, масло)',
    EVERYTHING: 'Купуваме и готвиме заедно',
    NO_PREFERENCE: 'Не ми е важно',
  },
  parties: { NONE: 'Без забави', SMALL: 'Мали дружења повремено', PARTIES: 'Сакам забави дома' },
  partnerStays: { NO: 'Не', SOMETIMES: 'Повремено, со договор', YES: 'Без проблем' },
} as const

/** Short forms of the same answers, for cards and the profile page. */
export const SHORT = {
  cleaning: { WHEN_POSSIBLE: 'кога ќе стигне', SCHEDULE: 'по распоред', REGULAR: 'редовно' },
  sound: { HEADPHONES: 'со слушалки', QUIET_SPEAKER: 'тивко', LOUD: 'гласно', NO_PREFERENCE: 'без разлика' },
  communication: { MINIMAL: 'минимална', OCCASIONAL: 'повремена', TOGETHER: 'многу', NO_PREFERENCE: 'без разлика' },
  sharing: { SEPARATE: 'секој свое', BASICS: 'основни работи', EVERYTHING: 'сè заедно', NO_PREFERENCE: 'без разлика' },
  parties: { NONE: 'без забави', SMALL: 'мали дружења', PARTIES: 'сака забави' },
  partnerStays: { NO: 'не', SOMETIMES: 'повремено', YES: 'без проблем' },
} as const
