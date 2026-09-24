package com.study.grabthisforme.persistence.repository;
import com.study.grabthisforme.persistence.entity.CommentSendReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CommentSendReceiptRepository extends JpaRepository<CommentSendReceiptEntity, String> {
    Optional<CommentSendReceiptEntity> findByUserIdAndRequestId(Long userId, String requestId);
}
