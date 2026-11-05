package mk.roommatematch.listing;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mk.roommatematch.listing.ListingDtos.ListingRequest;
import mk.roommatematch.listing.ListingDtos.PropertyType;
import mk.roommatematch.reference.Area;

@Entity
@Table(name = "listings")
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ownerId;

    @Enumerated(EnumType.STRING)
    private PropertyType propertyType;

    private String address;

    @Enumerated(EnumType.STRING)
    private Area area;

    private double latitude;

    private double longitude;

    private int monthlyRent;

    private short freeSpots;

    private String description;

    private String inviteCode;

    @Column(insertable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "listing_residents", joinColumns = @JoinColumn(name = "listing_id"))
    @Column(name = "user_id")
    private Set<Long> residentIds = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "listing_id", nullable = false)
    @OrderBy("id")
    private List<ListingImage> images = new ArrayList<>();

    protected Listing() {
    }

    public Listing(Long ownerId, String inviteCode) {
        this.ownerId = ownerId;
        this.inviteCode = inviteCode;
        residentIds.add(ownerId);
    }

    public void update(ListingRequest request) {
        propertyType = request.propertyType();
        address = request.address().trim();
        area = request.area();
        latitude = request.latitude();
        longitude = request.longitude();
        monthlyRent = request.monthlyRent();
        freeSpots = request.freeSpots().shortValue();
        description = request.description().trim();
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public PropertyType getPropertyType() {
        return propertyType;
    }

    public String getAddress() {
        return address;
    }

    public Area getArea() {
        return area;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getMonthlyRent() {
        return monthlyRent;
    }

    public int getFreeSpots() {
        return freeSpots;
    }

    public String getDescription() {
        return description;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public Set<Long> getResidentIds() {
        return residentIds;
    }

    public List<ListingImage> getImages() {
        return images;
    }
}
