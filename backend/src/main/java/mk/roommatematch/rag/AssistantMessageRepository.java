package mk.roommatematch.rag;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {

    List<AssistantMessage> findByUserIdOrderByIdAsc(Long userId);

    List<AssistantMessage> findTop6ByUserIdOrderByIdDesc(Long userId);

    @Modifying
    @Transactional
    void deleteByUserId(Long userId);
}
