package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.StoreTagEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreTagRepository extends JpaRepository<StoreTagEntity, String> {

    List<StoreTagEntity> findAllByStoreId(Long storeId);

    List<StoreTagEntity> findAllByStoreIdIn(List<Long> storeIds);

    void deleteAllByStoreId(Long storeId);
}
