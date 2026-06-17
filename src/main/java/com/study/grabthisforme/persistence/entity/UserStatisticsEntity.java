package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_statistics")
public class UserStatisticsEntity {

    @Id
    public Long userId;
    public Long likeCount;
    public Long fanCount;
    public Long followCount;

    public UserStatisticsEntity() {
    }

    public UserStatisticsEntity(Long userId, Long likeCount, Long fanCount, Long followCount) {
        this.userId = userId;
        this.likeCount = likeCount;
        this.fanCount = fanCount;
        this.followCount = followCount;
    }
}
