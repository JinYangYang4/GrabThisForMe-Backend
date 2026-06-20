package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_cache")
public class PostEntity {

    @Id
    public String postId;
    public String content;
    public String imagesJson;
    public String categoryKey;
    public Long createTime;

    public PostEntity() {
    }
}