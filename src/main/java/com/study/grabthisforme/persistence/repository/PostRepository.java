package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.PostEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<PostEntity, String> {

    List<PostEntity> findAllByOrderByCreateTimeDesc();

    List<PostEntity> findByCreateTimeLessThanOrderByCreateTimeDesc(long createTime, Pageable pageable);

    List<PostEntity> findByCategoryKeyAndCreateTimeLessThanOrderByCreateTimeDesc(
        String categoryKey,
        long createTime,
        Pageable pageable
    );

    long countByCategoryKey(String categoryKey);
}