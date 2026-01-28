package com.example.smartmoney.infrastructure.persistence.mappers;
import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.infrastructure.persistence.entities.OTPEntity;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import org.mapstruct.*;
import org.springframework.stereotype.Component;

@Component
@Mapper(componentModel = "spring")
public abstract class OTPMapper {

    @Mapping(target = "id", source = "domain.id")
    @Mapping(target = "email", expression = "java(mapEmailToEntity(domain.getEmail()))")
    @Mapping(target = "otp", source = "domain.otp")
    @Mapping(target = "expiredAt", source = "domain.expiredAt")
    @Mapping(target = "verified", source = "domain.verified")
    public abstract OTPEntity toEntity(OTP domain);

    @Mapping(target = "id", source = "entity.id")
    @Mapping(target = "email", expression = "java(mapEmailToDomain(entity.getEmail()))")
    @Mapping(target = "otp", source = "entity.otp")
    @Mapping(target = "expiredAt", source = "entity.expiredAt")
    @Mapping(target = "verified", source = "entity.verified")
    public abstract OTP toDomain(OTPEntity entity);

    // Domain → Entity
    protected String mapEmailToEntity(Email email) {
        return email != null ? email.getValue() : null;
    }

    // Entity → Domain
    protected Email mapEmailToDomain(String email) {
        return email == null ? null : new Email(email);
    }
}

