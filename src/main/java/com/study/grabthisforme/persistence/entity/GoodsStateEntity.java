package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "goods_state")
public class GoodsStateEntity {

    @Id
    public Long goodsId;
    public Long saleNumber;
    public Integer stock;
    public Boolean isSoldOut;
    public Boolean isHot;
    public Integer purchaseStatus;
    public Long soldCount;

    public GoodsStateEntity() {
    }

    public GoodsStateEntity(
        Long goodsId,
        Long saleNumber,
        Integer stock,
        Boolean isSoldOut,
        Boolean isHot,
        Integer purchaseStatus,
        Long soldCount
    ) {
        this.goodsId = goodsId;
        this.saleNumber = saleNumber;
        this.stock = stock;
        this.isSoldOut = isSoldOut;
        this.isHot = isHot;
        this.purchaseStatus = purchaseStatus;
        this.soldCount = soldCount;
    }
}
