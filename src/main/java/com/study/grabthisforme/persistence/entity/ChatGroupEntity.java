package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_group")
public class ChatGroupEntity {

    @Id
    public Long groupId;
    public String groupName;
    public Long createTime;

    public ChatGroupEntity() {
    }
}
