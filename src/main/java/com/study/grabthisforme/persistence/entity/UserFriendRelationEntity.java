package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_friend_relation")
public class UserFriendRelationEntity {

    @Id
    public String id;
    public Long userId;
    public Long friendUserId;
    public String status;
    public Long addedTime;

    public UserFriendRelationEntity() {
    }

    public UserFriendRelationEntity(Long userId, Long friendUserId, String status, Long addedTime) {
        this.id = userId + ":" + friendUserId;
        this.userId = userId;
        this.friendUserId = friendUserId;
        this.status = status;
        this.addedTime = addedTime;
    }
}
