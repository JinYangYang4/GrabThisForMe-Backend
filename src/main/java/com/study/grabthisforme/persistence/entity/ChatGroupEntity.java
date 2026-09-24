package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "chat_group")
public class ChatGroupEntity {

    @Id
    public Long groupId;
    @Column(nullable=false, unique=true) public String conversationId;
    public String groupName;
    @Column(unique=true) public String creationKey;
    @Column(length=10000) public String creationFingerprint;
    public Long createTime;

    public ChatGroupEntity() {
    }
}
