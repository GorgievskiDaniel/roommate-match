package mk.roommatematch.listing;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "listing_images")
public class ListingImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fileName;

    /** The first photo, at position 0, is the main one. */
    private int position;

    protected ListingImage() {
    }

    public ListingImage(String fileName, int position) {
        this.fileName = fileName;
        this.position = position;
    }

    public int getPosition() {
        return position;
    }

    void moveTo(int position) {
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }
}
