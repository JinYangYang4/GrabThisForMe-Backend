package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_liked_goods")
public class UserLikedGoodsEntity {

    @Id
    public String id;
    public Long userId;
    public Long goodsId;
    public Long likedAt;

    public UserLikedGoodsEntity() {
    }

    public UserLikedGoodsEntity(Long userId, Long goodsId, Long likedAt) {
        this.id = userId + ":" + goodsId;
        this.userId = userId;
        this.goodsId = goodsId;
        this.likedAt = likedAt;
    }
}
