package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.UserLikedGoodsEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLikedGoodsRepository extends JpaRepository<UserLikedGoodsEntity, String> {

    List<UserLikedGoodsEntity> findAllByUserId(Long userId);

    Optional<UserLikedGoodsEntity> findByUserIdAndGoodsId(Long userId, Long goodsId);
}
