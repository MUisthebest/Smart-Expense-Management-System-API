package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.OTPEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface JpaOTPRepository extends JpaRepository<OTPEntity, Long> {
    Optional<OTPEntity> findByEmailAndVerifiedFalse(String email);
    @Query("SELECT COUNT(o) > 0 FROM OTPEntity o WHERE o.email = :email AND o.verified = false AND o.expiredAt > :now AND o.type = VERIFY")
    boolean existsValidByEmail(@Param("email") String email, @Param("now") LocalDateTime now);
    void deleteByEmail(String email);
}