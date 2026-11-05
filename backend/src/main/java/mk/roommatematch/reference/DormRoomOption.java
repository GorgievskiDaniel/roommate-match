package mk.roommatematch.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dorm_room_options")
public class DormRoomOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dorm_id", insertable = false, updatable = false)
    private Long dormId;

    private String name;

    protected DormRoomOption() {
    }

    public Long getId() {
        return id;
    }

    public Long getDormId() {
        return dormId;
    }

    public String getName() {
        return name;
    }
}
