package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.AuthSessionEntity;
import com.study.grabthisforme.persistence.entity.AuthSessionStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, String> {
    Optional<AuthSessionEntity> findByAccessTokenHash(String accessTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSessionEntity s where s.id = :id")
    Optional<AuthSessionEntity> findForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSessionEntity s where s.userId = :userId and s.status = com.study.grabthisforme.persistence.entity.AuthSessionStatus.ACTIVE")
    List<AuthSessionEntity> findActiveForUpdate(@Param("userId") Long userId);

    Optional<AuthSessionEntity> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, AuthSessionStatus status);

    List<AuthSessionEntity> findAllByStatusAndAbsoluteExpiresAtLessThan(AuthSessionStatus status, Long timestamp);

    List<AuthSessionEntity> findAllByStatusNotAndRevokedAtLessThan(AuthSessionStatus status, Long timestamp);
}
