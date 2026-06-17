package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_liked_store")
public class UserLikedStoreEntity {

    @Id
    public String id;
    public Long userId;
    public Long storeId;
    public Long likedAt;

    public UserLikedStoreEntity() {
    }

    public UserLikedStoreEntity(Long userId, Long storeId, Long likedAt) {
        this.id = userId + ":" + storeId;
        this.userId = userId;
        this.storeId = storeId;
        this.likedAt = likedAt;
    }
}
