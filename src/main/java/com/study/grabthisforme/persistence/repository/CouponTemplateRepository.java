package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.CouponTemplateEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponTemplateRepository extends JpaRepository<CouponTemplateEntity, Long> {
    List<CouponTemplateEntity> findAllByIsActiveTrueOrderByDiscountAmountDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select template from CouponTemplateEntity template where template.templateId = :templateId")
    Optional<CouponTemplateEntity> findByIdForUpdate(@Param("templateId") Long templateId);
}
