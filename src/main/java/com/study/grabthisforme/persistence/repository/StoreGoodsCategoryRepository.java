package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.StoreGoodsCategoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreGoodsCategoryRepository extends JpaRepository<StoreGoodsCategoryEntity, Long> {

    List<StoreGoodsCategoryEntity> findAllByStoreIdOrderBySortOrderAsc(Long storeId);
}
