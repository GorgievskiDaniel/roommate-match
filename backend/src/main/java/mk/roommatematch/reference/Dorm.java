package mk.roommatematch.reference;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
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
