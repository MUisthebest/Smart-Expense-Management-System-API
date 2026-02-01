package com.example.smartmoney.infrastructure.persistence.repositories;
import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import com.example.smartmoney.domain.repositories.OTPRepository;
import com.example.smartmoney.infrastructure.persistence.entities.OTPEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.OTPMapper;

@Repository
@RequiredArgsConstructor
public class OTPRepositoryImpl implements OTPRepository {
    private final JpaOTPRepository jpaOTPRepository;
    private final OTPMapper otpMapper;

    @Override
    public void save(OTP otp) {
        OTPEntity entity = otpMapper.toEntity(otp);
        jpaOTPRepository.save(entity);
    }

    @Override
    public Optional<OTP> findValidByEmail(Email email) {
        return jpaOTPRepository.findByEmailAndVerifiedFalse(email.getValue())
            .filter(otpEntity -> otpEntity.getExpiredAt().isAfter(java.time.LocalDateTime.now()))
            .map(otpMapper::toDomain);
    }

    @Override
    public boolean existsValidByEmail(Email email) {
        return jpaOTPRepository.existsValidByEmail(email.getValue(), LocalDateTime.now());
    }
    
    @Override
    public void deleteByEmail(Email email) {
        jpaOTPRepository.deleteByEmail(email.getValue());
    }
}
