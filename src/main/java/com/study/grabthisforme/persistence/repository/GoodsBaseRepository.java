package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.GoodsBaseEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsBaseRepository extends JpaRepository<GoodsBaseEntity, Long> {

    List<GoodsBaseEntity> findAllByStoreId(Long storeId);

    List<GoodsBaseEntity> findAllByNameContainingIgnoreCaseOrderByGoodsIdDesc(String keyword);
}
