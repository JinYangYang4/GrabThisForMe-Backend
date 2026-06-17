package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "goods_price")
public class GoodsPriceEntity {

    @Id
    public Long goodsId;
    public Double price;
    public Double discountPrice;
    public String discountTag;

    public GoodsPriceEntity() {
    }

    public GoodsPriceEntity(Long goodsId, Double price, Double discountPrice, String discountTag) {
        this.goodsId = goodsId;
        this.price = price;
        this.discountPrice = discountPrice;
        this.discountTag = discountTag;
    }
}
