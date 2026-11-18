package mk.roommatematch.profile;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    List<Profile> findByDormIdOrderByFirstName(Long dormId);

    /** Rows of [dorm id, number of students looking for a roommate there]. */
    @Query("select p.dorm.id, count(p) from Profile p where p.dorm is not null group by p.dorm.id")
    List<Object[]> countByDorm();

    /** Rows of [room option id, number of students looking for a roommate in that kind of room]. */
    @Query("select p.dormRoomOption.id, count(p) from Profile p where p.dormRoomOption is not null group by p.dormRoomOption.id")
    List<Object[]> countByRoomOption();
}
