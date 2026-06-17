package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.UserLikedPostEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLikedPostRepository extends JpaRepository<UserLikedPostEntity, String> {

    List<UserLikedPostEntity> findAllByUserId(Long userId);

    Optional<UserLikedPostEntity> findByUserIdAndPostId(Long userId, String postId);
}
