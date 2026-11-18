package mk.roommatematch.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A dorm student's ad that they are looking for a roommate in their dorm. */
@Entity
@Table(name = "dorm_posts")
public class DormPost {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "dorm_id")
    private Long dormId;

    private String text;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected DormPost() {
    }

    public DormPost(Long userId) {
        this.userId = userId;
    }

    public void write(Long dormId, String text) {
        this.dormId = dormId;
        this.text = text;
        this.updatedAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public Long getDormId() {
        return dormId;
    }

    public String getText() {
        return text;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
