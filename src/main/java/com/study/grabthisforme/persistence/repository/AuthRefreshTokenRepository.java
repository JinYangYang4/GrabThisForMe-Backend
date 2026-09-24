package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.AuthRefreshTokenEntity;
import com.study.grabthisforme.persistence.entity.AuthRefreshTokenStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshTokenEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AuthRefreshTokenEntity t where t.tokenHash = :tokenHash")
    Optional<AuthRefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<AuthRefreshTokenEntity> findAllBySessionIdAndStatus(String sessionId, AuthRefreshTokenStatus status);

    List<AuthRefreshTokenEntity> findAllBySessionId(String sessionId);

    Optional<AuthRefreshTokenEntity> findTopBySessionIdOrderByGenerationDesc(String sessionId);

    List<AuthRefreshTokenEntity> findAllByStatusNotAndCreatedAtLessThan(AuthRefreshTokenStatus status, Long timestamp);

    void deleteAllBySessionIdIn(Collection<String> sessionIds);
}
