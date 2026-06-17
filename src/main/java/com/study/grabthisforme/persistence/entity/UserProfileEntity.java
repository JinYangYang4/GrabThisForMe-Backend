package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profile")
public class UserProfileEntity {

    @Id
    public Long userId;
    public String displayName;
    public String avatarUrl;
    public String phone;
    public String email;
    public Integer gender;
    public Boolean isVip;
    public String signature;

    public UserProfileEntity() {
    }

    public UserProfileEntity(
        Long userId,
        String displayName,
        String avatarUrl,
        String phone,
        String email,
        Integer gender,
        Boolean isVip,
        String signature
    ) {
        this.userId = userId;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.phone = phone;
        this.email = email;
        this.gender = gender;
        this.isVip = isVip;
        this.signature = signature;
    }
}
