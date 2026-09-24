package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.UserCouponEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCouponRepository extends JpaRepository<UserCouponEntity, String> {
    List<UserCouponEntity> findAllByUserIdOrderByAcquiredAtDesc(Long userId);
    List<UserCouponEntity> findAllByUserIdAndStatusOrderByValidUntilAsc(Long userId, String status);
    long countByUserIdAndTemplateId(Long userId, Long templateId);
    Optional<UserCouponEntity> findByUserIdAndAcquisitionRequestId(Long userId, String acquisitionRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select coupon from UserCouponEntity coupon where coupon.userCouponId = :userCouponId")
    Optional<UserCouponEntity> findByIdForUpdate(@Param("userCouponId") String userCouponId);
}
