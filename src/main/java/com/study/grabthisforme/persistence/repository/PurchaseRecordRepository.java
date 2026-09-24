package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.PurchaseRecordEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRecordRepository extends JpaRepository<PurchaseRecordEntity, String> {

    List<PurchaseRecordEntity> findAllByBuyerIdOrderByCreatedTimeDesc(Long buyerId);

    List<PurchaseRecordEntity> findAllByBuyerIdAndClientPurchaseIdOrderByRecordIdAsc(
        Long buyerId,
        String clientPurchaseId
    );
}
