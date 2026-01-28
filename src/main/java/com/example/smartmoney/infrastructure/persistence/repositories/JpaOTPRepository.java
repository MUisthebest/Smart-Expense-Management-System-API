package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.OTPEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface JpaOTPRepository extends JpaRepository<OTPEntity, Long> {
    Optional<OTPEntity> findByEmailAndVerifiedFalse(String email);
    void deleteByEmail(String email);
}