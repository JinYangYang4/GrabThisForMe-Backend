package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.PostCommentEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCommentRepository extends JpaRepository<PostCommentEntity, Long> {

    List<PostCommentEntity> findAllByPostIdOrderByTimeAsc(String postId);
}
