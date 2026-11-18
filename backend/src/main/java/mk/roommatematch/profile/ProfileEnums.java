package mk.roommatematch.profile;

public final class ProfileEnums {

    private ProfileEnums() {
    }

    public enum Gender { MALE, FEMALE }

    public enum HousingType { APARTMENT, DORM }

    /** NEEDS_OR_TOGETHER is a student without a place who is open to both other ways of finding one. */
    public enum Situation {
        HAS_PLACE, NEEDS_PLACE, SEARCH_TOGETHER, NEEDS_OR_TOGETHER;

        /** Can move in with someone who has a place. */
        public boolean needsPlace() {
            return this == NEEDS_PLACE || this == NEEDS_OR_TOGETHER;
        }

        /** Can search for a place together with someone else who has none. */
        public boolean searchesTogether() {
            return this == SEARCH_TOGETHER || this == NEEDS_OR_TOGETHER;
        }
    }

    public enum GuestsFrequency { RARELY, SOMETIMES, OFTEN }

    /**
     * Each of these goes from less to more, so the distance between two answers can be measured.
     * NO_PREFERENCE ("Не ми е важно") is outside the scale: the question then does not count for that pair.
     */
    public enum CleaningStyle { WHEN_POSSIBLE, SCHEDULE, REGULAR }

    public enum SoundLevel { HEADPHONES, QUIET_SPEAKER, LOUD, NO_PREFERENCE }

    public enum CommunicationLevel { MINIMAL, OCCASIONAL, TOGETHER, NO_PREFERENCE }

    public enum SharingStyle { SEPARATE, BASICS, EVERYTHING, NO_PREFERENCE }

    public enum PartyAttitude { NONE, SMALL, PARTIES }

    public enum PartnerStays { NO, SOMETIMES, YES }

    public enum SleepSchedule { EARLY, FLEXIBLE, LATE }

    public enum GenderPreference { SAME, ANY }
}
