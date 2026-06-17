package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.UserAccountEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, Long> {

    Optional<UserAccountEntity> findByAccountName(String accountName);

    List<UserAccountEntity> findAllByUserIdIn(List<Long> userIds);
}
