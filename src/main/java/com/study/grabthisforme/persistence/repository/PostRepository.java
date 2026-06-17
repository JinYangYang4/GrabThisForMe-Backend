package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.PostEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<PostEntity, String> {

    List<PostEntity> findAllByOrderByCreateTimeDesc();
}
