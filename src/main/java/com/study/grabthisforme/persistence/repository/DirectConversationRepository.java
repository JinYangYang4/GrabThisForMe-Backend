package com.study.grabthisforme.persistence.repository;
import com.study.grabthisforme.persistence.entity.DirectConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface DirectConversationRepository extends JpaRepository<DirectConversationEntity,String> {
 Optional<DirectConversationEntity> findByUserLowIdAndUserHighId(Long low,Long high);
}
