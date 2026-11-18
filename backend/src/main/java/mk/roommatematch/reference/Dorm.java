package mk.roommatematch.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dorms")
public class Dorm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Double latitude;

    private Double longitude;

    private boolean roomTypeAffectsMatch;

    /** The part of town people know the dorm by, such as Автокоманда, or null. */
    private String neighbourhood;

    /** The folder of the dorm's photos. */
    private String slug;

    /** When a dorm still being built opens; null for dorms already in use. */
    @Column(name = "opens_on")
    private LocalDate opensOn;

    @OneToMany
    @JoinColumn(name = "dorm_id")
    @OrderBy("id")
    private List<DormRoomOption> roomOptions = new ArrayList<>();

    @OneToMany
    @JoinColumn(name = "dorm_id")
    @OrderBy("id")
    private List<DormBlock> blocks = new ArrayList<>();

    protected Dorm() {
    }

    public String getNeighbourhood() {
        return neighbourhood;
    }

    public String getSlug() {
        return slug;
    }

    public LocalDate getOpensOn() {
        return opensOn;
    }

    public boolean isOpen(LocalDate today) {
        return opensOn == null || !opensOn.isAfter(today);
    }

    /** For people to read, such as "Ќе биде пуштен во употреба во јануари 2027 година.", or null once open. */
    public String openingNote(LocalDate today) {
        if (isOpen(today)) {
            return null;
        }
        String[] months = {"јануари", "февруари", "март", "април", "мај", "јуни", "јули", "август", "септември",
                "октомври", "ноември", "декември"};
        return "Ќе биде пуштен во употреба во " + months[opensOn.getMonthValue() - 1] + " " + opensOn.getYear()
                + " година.";
    }

    public boolean isRoomTypeAffectsMatch() {
        return roomTypeAffectsMatch;
    }

    public List<DormBlock> getBlocks() {
        return blocks;
    }

    public List<DormRoomOption> getRoomOptions() {
        return roomOptions;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
