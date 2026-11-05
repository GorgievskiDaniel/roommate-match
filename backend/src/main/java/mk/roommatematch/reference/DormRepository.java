package mk.roommatematch.reference;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DormRepository extends JpaRepository<Dorm, Long> {

    @Query("select distinct d from Dorm d left join fetch d.roomOptions order by d.id")
    List<Dorm> findAllWithRoomOptions();
}
