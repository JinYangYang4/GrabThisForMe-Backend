package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "user_coupon",
    indexes = {
        @Index(name = "idx_user_coupon_owner_status", columnList = "userId,status"),
        @Index(
            name = "idx_user_coupon_acquire_request",
            columnList = "userId,acquisitionRequestId",
            unique = true
        )
    }
)
public class UserCouponEntity {
    @Id
    public String userCouponId;
    public Long templateId;
    public Long userId;
    public String acquisitionRequestId;
    public Double purchasePricePaid;
    public Long acquiredAt;
    public Long validFrom;
    public Long validUntil;
    public String status;
    public String usedPurchaseId;
    public Long usedAt;

    public UserCouponEntity() {
    }
}
