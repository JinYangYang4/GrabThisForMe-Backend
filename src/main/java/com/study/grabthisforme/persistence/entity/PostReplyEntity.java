package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_reply")
public class PostReplyEntity {

    @Id
    public Long replyId;
    public String postId;
    @jakarta.persistence.Column(length = 80)
    public String clientRequestId;
    public Long parentCommentId;
    public Long parentReplyId;
    public Long time;
    @jakarta.persistence.Column(length = 10000)
    public String message;
    public String imageUrlsJson;
    public Long commenterId;
    public String commenterName;
    public String commenterAvatarUrl;
    public Long beCommenterId;
    public String beCommenterName;
    public String beCommenterAvatarUrl;

    public PostReplyEntity() {
    }
}
