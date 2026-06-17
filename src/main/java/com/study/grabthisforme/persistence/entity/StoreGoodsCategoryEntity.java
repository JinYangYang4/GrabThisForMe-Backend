package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "store_goods_group")
public class StoreGoodsCategoryEntity {

    @Id
    public Long groupId;
    public Long storeId;
    public String category;
    public Integer sortOrder;

    public StoreGoodsCategoryEntity() {
    }

    public StoreGoodsCategoryEntity(Long groupId, Long storeId, String category, Integer sortOrder) {
        this.groupId = groupId;
        this.storeId = storeId;
        this.category = category;
        this.sortOrder = sortOrder;
    }
}
