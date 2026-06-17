package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.PostReplyEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface PostReplyRepository extends JpaRepository<PostReplyEntity, Long> {

    List<PostReplyEntity> findAllByPostIdOrderByTimeAsc(String postId);

    List<PostReplyEntity> findAllByParentCommentIdOrderByTimeAsc(Long parentCommentId, Pageable pageable);

    List<PostReplyEntity> findByParentCommentIdAndTimeLessThanOrderByTimeDesc(
        Long parentCommentId,
        Long time,
        Pageable pageable
    );

    long countByParentCommentId(Long parentCommentId);
}
