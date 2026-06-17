package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "store_goods_group_item")
public class StoreGoodsCategoryItemEntity {

    @Id
    public String id;
    public Long groupId;
    public Long goodsId;
    public Integer sortOrder;

    public StoreGoodsCategoryItemEntity() {
    }

    public StoreGoodsCategoryItemEntity(Long groupId, Long goodsId, Integer sortOrder) {
        this.id = groupId + ":" + goodsId;
        this.groupId = groupId;
        this.goodsId = goodsId;
        this.sortOrder = sortOrder;
    }
}
