package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.UserPostEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPostRepository extends JpaRepository<UserPostEntity, String> {

    List<UserPostEntity> findAllByUserId(Long userId);

    UserPostEntity findByPostId(String postId);
}
