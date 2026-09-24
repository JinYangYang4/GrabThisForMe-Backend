package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.GoodsStateEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoodsStateRepository extends JpaRepository<GoodsStateEntity, Long> {

    List<GoodsStateEntity> findAllByGoodsIdIn(List<Long> goodsIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from GoodsStateEntity state where state.goodsId = :goodsId")
    Optional<GoodsStateEntity> findByGoodsIdForUpdate(@Param("goodsId") Long goodsId);
}
