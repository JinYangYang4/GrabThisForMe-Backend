package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_custom_tag")
public class PostCustomTagEntity {

    @Id
    public String id;
    public String postId;
    public String tag;
    public String normalizedTag;
    public Integer sortOrder;
    public Long createTime;

    public PostCustomTagEntity() {
    }

    public PostCustomTagEntity(String postId, String tag, String normalizedTag, Integer sortOrder, Long createTime) {
        this.id = postId + ":" + normalizedTag;
        this.postId = postId;
        this.tag = tag;
        this.normalizedTag = normalizedTag;
        this.sortOrder = sortOrder;
        this.createTime = createTime;
    }
}