package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.PostReplyEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostReplyRepository extends JpaRepository<PostReplyEntity, Long> {

    List<PostReplyEntity> findAllByPostIdOrderByTimeAsc(String postId);
}
