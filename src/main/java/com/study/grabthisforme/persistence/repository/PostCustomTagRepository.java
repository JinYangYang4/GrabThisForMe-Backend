package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.PostCustomTagEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostCustomTagRepository extends JpaRepository<PostCustomTagEntity, String> {

    List<PostCustomTagEntity> findAllByPostIdOrderBySortOrderAsc(String postId);

    List<PostCustomTagEntity> findAllByPostIdIn(List<String> postIds);

    void deleteAllByPostId(String postId);
}