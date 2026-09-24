package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_follow", indexes = {
    @Index(name = "idx_follow_out_time", columnList = "userId,createdAt,targetId"),
    @Index(name = "idx_follow_in_time", columnList = "targetId,createdAt,userId"),
    @Index(name = "idx_follow_in_pair", columnList = "targetId,userId")
}, uniqueConstraints = @UniqueConstraint(name = "uk_follow_pair", columnNames = {"userId", "targetId"}))
public class UserFollowEntity {
    @Id public String id;
    @Column(nullable = false) public Long userId;
    @Column(nullable = false) public Long targetId;
    @Column(nullable = false) public Long createdAt;
    public UserFollowEntity() {}
    public UserFollowEntity(long userId, long targetId) {
        this.id = userId + ":" + targetId;
        this.userId = userId;
        this.targetId = targetId;
        this.createdAt = System.currentTimeMillis();
    }
}
