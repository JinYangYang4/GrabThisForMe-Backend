package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.StoreEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<StoreEntity, Long> {

    List<StoreEntity> findAllByOwnerId(Long ownerId);

    List<StoreEntity> findAllByNameContainingIgnoreCaseOrderByStoreIdDesc(String keyword);
}
