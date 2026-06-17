package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_post")
public class UserPostEntity {

    @Id
    public String id;
    public Long userId;
    public String postId;

    public UserPostEntity() {
    }

    public UserPostEntity(Long userId, String postId) {
        this.id = userId + ":" + postId;
        this.userId = userId;
        this.postId = postId;
    }
}
