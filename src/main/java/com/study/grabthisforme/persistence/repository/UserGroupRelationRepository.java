package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.UserGroupRelationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGroupRelationRepository extends JpaRepository<UserGroupRelationEntity, String> {

    List<UserGroupRelationEntity> findAllByUserId(Long userId);

    List<UserGroupRelationEntity> findAllByGroupId(Long groupId);
}
