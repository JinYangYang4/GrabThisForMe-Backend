package com.study.grabthisforme.persistence.repository;
import com.study.grabthisforme.persistence.entity.PasswordResetChallenge;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface PasswordResetRepository extends JpaRepository<PasswordResetChallenge,String> {
    long countByPhoneAndCreatedAtGreaterThan(String phone,long since);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from PasswordResetChallenge c where c.challengeId=:id")
    Optional<PasswordResetChallenge> findForUpdate(@Param("id") String id);
}
