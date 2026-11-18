package mk.roommatematch.profile;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DormPostRepository extends JpaRepository<DormPost, Long> {

    List<DormPost> findByDormId(Long dormId);
}
