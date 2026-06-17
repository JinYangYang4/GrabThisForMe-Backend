package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.StoreGoodsCategoryItemEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreGoodsCategoryItemRepository extends JpaRepository<StoreGoodsCategoryItemEntity, String> {

    List<StoreGoodsCategoryItemEntity> findAllByGroupIdIn(List<Long> groupIds);

    void deleteAllByGroupId(Long groupId);
}
