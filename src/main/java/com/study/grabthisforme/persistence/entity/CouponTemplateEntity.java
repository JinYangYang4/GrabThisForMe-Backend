package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "coupon_template")
public class CouponTemplateEntity {
    @Id
    public Long templateId;
    public String title;
    public String description;
    public Double discountAmount;
    public Double minimumAmount;
    public Double purchasePrice;
    public Integer validDays;
    public Long storeId;
    public Integer stock;
    public Long soldCount;
    public Integer perUserLimit;
    public Boolean isActive;

    public CouponTemplateEntity() {
    }

    public CouponTemplateEntity(
        Long templateId, String title, String description, Double discountAmount,
        Double minimumAmount, Double purchasePrice, Integer validDays, Long storeId,
        Integer stock, Long soldCount, Integer perUserLimit, Boolean isActive
    ) {
        this.templateId = templateId;
        this.title = title;
        this.description = description;
        this.discountAmount = discountAmount;
        this.minimumAmount = minimumAmount;
        this.purchasePrice = purchasePrice;
        this.validDays = validDays;
        this.storeId = storeId;
        this.stock = stock;
        this.soldCount = soldCount;
        this.perUserLimit = perUserLimit;
        this.isActive = isActive;
    }
}
