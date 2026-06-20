package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.PostCommentEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCommentRepository extends JpaRepository<PostCommentEntity, Long> {

    List<PostCommentEntity> findAllByPostIdOrderByTimeAsc(String postId);

    List<PostCommentEntity> findAllByPostIdOrderByTimeAsc(String postId, Pageable pageable);

    List<PostCommentEntity> findByPostIdAndTimeLessThanOrderByTimeDesc(
        String postId,
        Long time,
        Pageable pageable
    );

    long countByPostId(String postId);
}
