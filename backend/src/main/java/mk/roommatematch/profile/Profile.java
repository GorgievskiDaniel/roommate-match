package mk.roommatematch.profile;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import mk.roommatematch.listing.ListingDtos.PropertyType;
import mk.roommatematch.profile.ProfileDtos.ProfileRequest;
import mk.roommatematch.profile.ProfileEnums.CleaningStyle;
import mk.roommatematch.profile.ProfileEnums.CommunicationLevel;
import mk.roommatematch.profile.ProfileEnums.Gender;
import mk.roommatematch.profile.ProfileEnums.GenderPreference;
import mk.roommatematch.profile.ProfileEnums.GuestsFrequency;
import mk.roommatematch.profile.ProfileEnums.HousingType;
import mk.roommatematch.profile.ProfileEnums.PartnerStays;
import mk.roommatematch.profile.ProfileEnums.PartyAttitude;
import mk.roommatematch.profile.ProfileEnums.SharingStyle;
import mk.roommatematch.profile.ProfileEnums.Situation;
import mk.roommatematch.profile.ProfileEnums.SleepSchedule;
import mk.roommatematch.profile.ProfileEnums.SoundLevel;
import mk.roommatematch.reference.Area;
import mk.roommatematch.reference.Dorm;
import mk.roommatematch.reference.DormRoomOption;
import mk.roommatematch.reference.Hobby;

@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    private String firstName;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private LocalDate birthDate;

    private String faculty;

    private String bio;

    @Enumerated(EnumType.STRING)
    private HousingType housingType;

    @Enumerated(EnumType.STRING)
    private Situation situation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dorm_id")
    private Dorm dorm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dorm_room_option_id")
    private DormRoomOption dormRoomOption;

    private Integer budgetMin;

    private Integer budgetMax;

    @ElementCollection
    @CollectionTable(name = "profile_areas", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "area")
    @Enumerated(EnumType.STRING)
    private Set<Area> areas = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "profile_property_types", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "property_type")
    @Enumerated(EnumType.STRING)
    private Set<PropertyType> propertyTypes = new HashSet<>();

    private boolean smoker;

    private boolean hasPets;

    @Enumerated(EnumType.STRING)
    private GuestsFrequency guestsFrequency;

    @Enumerated(EnumType.STRING)
    private CleaningStyle cleaning;

    @Enumerated(EnumType.STRING)
    private SleepSchedule sleepSchedule;

    @Enumerated(EnumType.STRING)
    private SoundLevel sound;

    @Enumerated(EnumType.STRING)
    private CommunicationLevel communication;

    @Enumerated(EnumType.STRING)
    private SharingStyle sharing;

    @Enumerated(EnumType.STRING)
    private PartyAttitude parties;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_stays")
    private PartnerStays partnerStays;

    private boolean acceptsSmoker;

    private boolean acceptsPets;

    @Enumerated(EnumType.STRING)
    @Column(name = "roommate_gender_preference")
    private GenderPreference roommateGenderPreference;

    @ManyToMany
    @JoinTable(name = "profile_hobbies",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "hobby_id"))
    private Set<Hobby> hobbies = new HashSet<>();

    /** The stored file name of the student's own picture, or null for the drawing. */
    private String avatar;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Column(insertable = false)
    private Instant updatedAt;

    protected Profile() {
    }

    public Profile(Long userId) {
        this.userId = userId;
    }

    /** Fields of the housing type that was not chosen are cleared, so stale answers cannot affect matching. */
    public void update(ProfileRequest request, Dorm chosenDorm, DormRoomOption chosenRoomOption, Set<Hobby> chosenHobbies) {
        firstName = request.firstName().trim();
        gender = request.gender();
        birthDate = request.birthDate();
        faculty = request.faculty().trim();
        bio = request.bio() == null ? "" : request.bio().trim();
        housingType = request.housingType();
        boolean apartment = housingType == HousingType.APARTMENT;
        situation = apartment ? request.situation() : null;
        dorm = apartment ? null : chosenDorm;
        dormRoomOption = apartment ? null : chosenRoomOption;
        // Someone who has a place lists it: the rent and the location come from the listing.
        boolean searching = apartment && situation != Situation.HAS_PLACE;
        budgetMin = searching ? request.budgetMin() : null;
        budgetMax = searching ? request.budgetMax() : null;
        areas.clear();
        propertyTypes.clear();
        if (apartment) {
            propertyTypes.addAll(request.propertyTypes());
        }
        if (searching) {
            areas.addAll(request.areas());
        }
        // Dorms do not allow smoking or pets, so for them these answers are not asked and stay false.
        smoker = apartment && Boolean.TRUE.equals(request.smoker());
        hasPets = apartment && Boolean.TRUE.equals(request.hasPets());
        guestsFrequency = request.guestsFrequency();
        cleaning = request.cleaning();
        sleepSchedule = request.sleepSchedule();
        sound = request.sound();
        communication = request.communication();
        sharing = request.sharing();
        parties = request.parties();
        partnerStays = request.partnerStays();
        acceptsSmoker = apartment && Boolean.TRUE.equals(request.acceptsSmoker());
        acceptsPets = apartment && Boolean.TRUE.equals(request.acceptsPets());
        roommateGenderPreference = request.roommateGenderPreference();
        hobbies.clear();
        hobbies.addAll(chosenHobbies);
        updatedAt = Instant.now();
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Long getUserId() {
        return userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public Gender getGender() {
        return gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getFaculty() {
        return faculty;
    }

    public String getBio() {
        return bio;
    }

    public HousingType getHousingType() {
        return housingType;
    }

    public Situation getSituation() {
        return situation;
    }

    public Dorm getDorm() {
        return dorm;
    }

    public DormRoomOption getDormRoomOption() {
        return dormRoomOption;
    }

    public Integer getBudgetMin() {
        return budgetMin;
    }

    public Integer getBudgetMax() {
        return budgetMax;
    }

    public Set<Area> getAreas() {
        return areas;
    }

    public Set<PropertyType> getPropertyTypes() {
        return propertyTypes;
    }

    public boolean isSmoker() {
        return smoker;
    }

    public boolean isHasPets() {
        return hasPets;
    }

    public GuestsFrequency getGuestsFrequency() {
        return guestsFrequency;
    }

    public CleaningStyle getCleaning() {
        return cleaning;
    }

    public SleepSchedule getSleepSchedule() {
        return sleepSchedule;
    }

    public SoundLevel getSound() {
        return sound;
    }

    public CommunicationLevel getCommunication() {
        return communication;
    }

    public SharingStyle getSharing() {
        return sharing;
    }

    public PartyAttitude getParties() {
        return parties;
    }

    public PartnerStays getPartnerStays() {
        return partnerStays;
    }

    public boolean isAcceptsSmoker() {
        return acceptsSmoker;
    }

    public boolean isAcceptsPets() {
        return acceptsPets;
    }

    public GenderPreference getRoommateGenderPreference() {
        return roommateGenderPreference;
    }

    public Set<Hobby> getHobbies() {
        return hobbies;
    }
}
