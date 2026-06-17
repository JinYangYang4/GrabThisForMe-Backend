package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.UserProfileEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, Long> {

    List<UserProfileEntity> findAllByUserIdIn(List<Long> userIds);
}
