package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.UserAccountEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserAccountEntity u where u.userId = :id")
    Optional<UserAccountEntity> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<UserAccountEntity> findByAccountName(String accountName);

    List<UserAccountEntity> findAllByUserIdIn(List<Long> userIds);
}
