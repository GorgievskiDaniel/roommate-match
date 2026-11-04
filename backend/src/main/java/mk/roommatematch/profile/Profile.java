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
import mk.roommatematch.profile.ProfileDtos.ProfileRequest;
import mk.roommatematch.profile.ProfileEnums.Gender;
import mk.roommatematch.profile.ProfileEnums.GenderPreference;
import mk.roommatematch.profile.ProfileEnums.GuestsFrequency;
import mk.roommatematch.profile.ProfileEnums.HousingType;
import mk.roommatematch.profile.ProfileEnums.SleepSchedule;
import mk.roommatematch.profile.ProfileEnums.Situation;
import mk.roommatematch.reference.Area;
import mk.roommatematch.reference.Dorm;
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

    private Integer budgetMin;

    private Integer budgetMax;

    @ElementCollection
    @CollectionTable(name = "profile_areas", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "area")
    @Enumerated(EnumType.STRING)
    private Set<Area> areas = new HashSet<>();

    private boolean smoker;

    private boolean hasPets;

    @Enumerated(EnumType.STRING)
    private GuestsFrequency guestsFrequency;

    private short tidiness;

    @Enumerated(EnumType.STRING)
    private SleepSchedule sleepSchedule;

    private short quietNeed;

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
    public void update(ProfileRequest request, Dorm chosenDorm, Set<Hobby> chosenHobbies) {
        firstName = request.firstName().trim();
        gender = request.gender();
        birthDate = request.birthDate();
        faculty = request.faculty().trim();
        bio = request.bio() == null ? "" : request.bio().trim();
        housingType = request.housingType();
        boolean apartment = housingType == HousingType.APARTMENT;
        situation = apartment ? request.situation() : null;
        dorm = apartment ? null : chosenDorm;
        budgetMin = apartment ? request.budgetMin() : null;
        budgetMax = apartment ? request.budgetMax() : null;
        areas.clear();
        if (apartment) {
            areas.addAll(request.areas());
        }
        smoker = request.smoker();
        hasPets = request.hasPets();
        guestsFrequency = request.guestsFrequency();
        tidiness = request.tidiness().shortValue();
        sleepSchedule = request.sleepSchedule();
        quietNeed = request.quietNeed().shortValue();
        acceptsSmoker = request.acceptsSmoker();
        acceptsPets = request.acceptsPets();
        roommateGenderPreference = request.roommateGenderPreference();
        hobbies.clear();
        hobbies.addAll(chosenHobbies);
        updatedAt = Instant.now();
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

    public Integer getBudgetMin() {
        return budgetMin;
    }

    public Integer getBudgetMax() {
        return budgetMax;
    }

    public Set<Area> getAreas() {
        return areas;
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

    public int getTidiness() {
        return tidiness;
    }

    public SleepSchedule getSleepSchedule() {
        return sleepSchedule;
    }

    public int getQuietNeed() {
        return quietNeed;
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
