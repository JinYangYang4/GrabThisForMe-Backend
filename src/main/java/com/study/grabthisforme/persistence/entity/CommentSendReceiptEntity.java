package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "comment_send_receipt", uniqueConstraints = @UniqueConstraint(name = "uk_comment_send_user_request", columnNames = {"user_id", "request_id"}))
public class CommentSendReceiptEntity {
    @Id public String receiptId;
    @Column(name = "user_id", nullable = false) public Long userId;
    @Column(name = "request_id", nullable = false, length = 80) public String requestId;
    @Column(nullable = false, length = 64) public String payloadHash;
    @Column(nullable = false) public String kind;
    @Column(nullable = false) public String postId;
    @Lob @Column(nullable = false) public String resultJson;
    public Long createdAt;
}
