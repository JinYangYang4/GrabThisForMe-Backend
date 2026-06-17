package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "store_tag")
public class StoreTagEntity {

    @Id
    public String id;
    public Long storeId;
    public String tag;
    public Integer sortOrder;

    public StoreTagEntity() {
    }

    public StoreTagEntity(Long storeId, String tag, Integer sortOrder) {
        this.id = storeId + ":" + tag;
        this.storeId = storeId;
        this.tag = tag;
        this.sortOrder = sortOrder;
    }
}
