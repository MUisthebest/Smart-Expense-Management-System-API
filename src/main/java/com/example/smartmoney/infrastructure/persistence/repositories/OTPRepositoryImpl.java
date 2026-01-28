package com.example.smartmoney.infrastructure.persistence.repositories;
import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import com.example.smartmoney.domain.repositories.OTPRepository;
import com.example.smartmoney.infrastructure.persistence.entities.OTPEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.OTPMapper;

@Repository
@RequiredArgsConstructor
public class OTPRepositoryImpl implements OTPRepository {
    private final JpaOTPRepository jpaOTPRepository;
    private final OTPMapper otpMapper;

    public void save(OTP otp) {
        OTPEntity entity = otpMapper.toEntity(otp);
        jpaOTPRepository.save(entity);
    }

    public Optional<OTP> findValidByEmail(Email email) {
        return jpaOTPRepository.findByEmailAndVerifiedFalse(email.getValue())
            .filter(otpEntity -> otpEntity.getExpiredAt().isAfter(java.time.LocalDateTime.now()))
            .map(otpMapper::toDomain);
    }

    public void deleteByEmail(Email email) {
        jpaOTPRepository.deleteByEmail(email.getValue());
    }
}
