package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_liked_post")
public class UserLikedPostEntity {

    @Id
    public String id;
    public Long userId;
    public String postId;
    public Long likedAt;

    public UserLikedPostEntity() {
    }

    public UserLikedPostEntity(Long userId, String postId, Long likedAt) {
        this.id = userId + ":" + postId;
        this.userId = userId;
        this.postId = postId;
        this.likedAt = likedAt;
    }
}
