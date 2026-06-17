package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "goods_base")
public class GoodsBaseEntity {

    @Id
    public Long goodsId;
    public Long storeId;
    public String name;
    public String message;
    public String categoryKey;

    public GoodsBaseEntity() {
    }

    public GoodsBaseEntity(Long goodsId, Long storeId, String name, String message, String categoryKey) {
        this.goodsId = goodsId;
        this.storeId = storeId;
        this.name = name;
        this.message = message;
        this.categoryKey = categoryKey;
    }
}
