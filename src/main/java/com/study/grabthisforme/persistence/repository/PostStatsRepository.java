package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.PostStatsEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostStatsRepository extends JpaRepository<PostStatsEntity, String> {

    List<PostStatsEntity> findAllByPostIdIn(List<String> postIds);
}
