package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.UserLikedStoreEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLikedStoreRepository extends JpaRepository<UserLikedStoreEntity, String> {

    List<UserLikedStoreEntity> findAllByUserId(Long userId);

    Optional<UserLikedStoreEntity> findByUserIdAndStoreId(Long userId, Long storeId);
}
