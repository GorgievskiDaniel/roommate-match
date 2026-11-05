package mk.roommatematch.listing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    Optional<Listing> findByInviteCode(String inviteCode);

    @Query("select l from Listing l where :userId member of l.residentIds")
    Optional<Listing> findByResident(@Param("userId") Long userId);

    /** A listing appears on the map only once it has at least one photo. */
    @Query("select distinct l from Listing l join fetch l.images")
    List<Listing> findAllWithImages();
}
