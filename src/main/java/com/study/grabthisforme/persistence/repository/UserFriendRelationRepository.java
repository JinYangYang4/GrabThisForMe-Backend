package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.UserFriendRelationEntity;
import java.util.List;
import com.study.grabthisforme.persistence.entity.FriendRelationId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFriendRelationRepository extends JpaRepository<UserFriendRelationEntity, FriendRelationId> {

    List<UserFriendRelationEntity> findAllByUserId(Long userId);

    List<UserFriendRelationEntity> findAllByFriendUserId(Long friendUserId);

    Optional<UserFriendRelationEntity> findByUserIdAndFriendUserId(Long userId, Long friendUserId);
}
