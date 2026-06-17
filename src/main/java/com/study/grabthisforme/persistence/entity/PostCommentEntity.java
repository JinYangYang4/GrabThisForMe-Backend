package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_comment")
public class PostCommentEntity {

    @Id
    public Long commentId;
    public String postId;
    public Long time;
    public String message;
    public String imageUrlsJson;
    public Long commenterId;
    public String commenterName;
    public String commenterAvatarUrl;

    public PostCommentEntity() {
    }
}
