package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_account")
public class UserAccountEntity {

    @Id
    public Long userId;
    public String accountName;
    public String passwordHash;
    public Boolean isCurrent;
    public Boolean isLoginAccount;
    public Long createTime;
    public Long lastLoginTime;

    public UserAccountEntity() {
    }

    public UserAccountEntity(
        Long userId,
        String accountName,
        String passwordHash,
        Boolean isCurrent,
        Boolean isLoginAccount,
        Long createTime,
        Long lastLoginTime
    ) {
        this.userId = userId;
        this.accountName = accountName;
        this.passwordHash = passwordHash;
        this.isCurrent = isCurrent;
        this.isLoginAccount = isLoginAccount;
        this.createTime = createTime;
        this.lastLoginTime = lastLoginTime;
    }
}
