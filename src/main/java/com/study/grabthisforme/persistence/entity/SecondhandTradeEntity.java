package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "secondhand_trade")
public class SecondhandTradeEntity {

    @Id
    public Long goodsId;
    public Long saleUserId;
    public Double originalPrice;
    public String quality;
    public String usedTime;
    public Integer tradeStatus;
    public Boolean negotiable;

    public SecondhandTradeEntity() {
    }

    public SecondhandTradeEntity(
        Long goodsId,
        Long saleUserId,
        Double originalPrice,
        String quality,
        String usedTime,
        Integer tradeStatus,
        Boolean negotiable
    ) {
        this.goodsId = goodsId;
        this.saleUserId = saleUserId;
        this.originalPrice = originalPrice;
        this.quality = quality;
        this.usedTime = usedTime;
        this.tradeStatus = tradeStatus;
        this.negotiable = negotiable;
    }
}
