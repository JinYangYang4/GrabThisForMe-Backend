package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_group_relation")
public class UserGroupRelationEntity {

    @Id
    public String id;
    public Long userId;
    public Long groupId;
    public String role;
    public Long joinedTime;

    public UserGroupRelationEntity() {
    }

    public UserGroupRelationEntity(Long userId, Long groupId, String role, Long joinedTime) {
        this.id = userId + ":" + groupId;
        this.userId = userId;
        this.groupId = groupId;
        this.role = role;
        this.joinedTime = joinedTime;
    }
}
