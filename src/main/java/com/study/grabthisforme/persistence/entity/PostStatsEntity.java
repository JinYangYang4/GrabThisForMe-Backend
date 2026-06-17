package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_stats")
public class PostStatsEntity {

    @Id
    public String postId;
    public Integer likeCount;
    public Integer commentCount;

    public PostStatsEntity() {
    }
}
