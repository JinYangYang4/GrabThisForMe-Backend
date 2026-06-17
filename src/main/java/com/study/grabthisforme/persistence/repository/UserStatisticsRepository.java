package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.UserStatisticsEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStatisticsRepository extends JpaRepository<UserStatisticsEntity, Long> {

    List<UserStatisticsEntity> findAllByUserIdIn(List<Long> userIds);
}
