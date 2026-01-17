package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.*;
import com.example.smartmoney.domain.models.user.enums.Role;
import com.example.smartmoney.infrastructure.persistence.entities.UserEntity;
import org.mapstruct.*;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Mapper(componentModel = "spring")
public abstract class UserMapper {
    
    // Domain -> Entity
    @Mapping(target = "passwordHash", source = "password.hash")
    @Mapping(target = "email", expression = "java(mapEmailToEntity(domain.getEmail()))")
    @Mapping(target = "phone", expression = "java(mapPhoneToEntity(domain.getPhone()))")
    @Mapping(target = "dateOfBirth", expression = "java(mapDateOfBirthToEntity(domain.getDateOfBirth()))")
    @Mapping(target = "avatar", expression = "java(mapAvatarToEntity(domain.getAvatar()))")
    @Mapping(target = "refreshToken", expression = "java(mapRefreshTokenToEntity(domain.getRefreshToken()))")
    @Mapping(target = "refreshTokenExpiry", expression = "java(mapRefreshTokenExpiryToEntity(domain.getRefreshToken()))")
    @Mapping(target = "role", expression = "java(mapRoleToEntity(domain.getRole()))")
    public abstract UserEntity toEntity(User domain);
    
    // Entity -> Domain
    @Mapping(target = "password", expression = "java(mapPasswordToDomain(entity.getPasswordHash()))")
    @Mapping(target = "email", expression = "java(mapEmailToDomain(entity.getEmail()))")
    @Mapping(target = "phone", expression = "java(mapPhoneToDomain(entity.getPhone()))")
    @Mapping(target = "dateOfBirth", expression = "java(mapDateOfBirthToDomain(entity.getDateOfBirth()))")
    @Mapping(target = "avatar", expression = "java(mapAvatarToDomain(entity.getAvatar()))")
    @Mapping(target = "refreshToken", expression = "java(mapRefreshTokenToDomain(entity.getRefreshToken(), entity.getRefreshTokenExpiry()))")
    @Mapping(target = "role", expression = "java(mapRoleToDomain(entity.getRole()))")
    public abstract User toDomain(UserEntity entity);
    
    // Helper methods for Domain -> Entity
    protected String mapEmailToEntity(Email email) {
        return email != null ? email.getValue() : null;
    }
    
    protected String mapPhoneToEntity(Phone phone) {
        return phone != null ? phone.getValue() : null;
    }
    
    protected LocalDate mapDateOfBirthToEntity(DateOfBirth dateOfBirth) {
        return dateOfBirth != null ? dateOfBirth.getValue() : null;
    }
    
    protected String mapAvatarToEntity(Avatar avatar) {
        return avatar != null ? avatar.getUrl() : null;
    }
    
    protected String mapRefreshTokenToEntity(RefreshToken refreshToken) {
        return refreshToken != null ? refreshToken.getToken() : null;
    }
    
    protected LocalDateTime mapRefreshTokenExpiryToEntity(RefreshToken refreshToken) {
        return refreshToken != null ? refreshToken.getExpiresAt() : null;
    }
    
    protected UserEntity.Role mapRoleToEntity(Role role) {
        if (role == null) return UserEntity.Role.USER;
        return switch (role) {
            case ADMIN -> UserEntity.Role.ADMIN;
            case USER -> UserEntity.Role.USER;
        };
    }
    
    // Helper methods for Entity -> Domain
    protected Password mapPasswordToDomain(String passwordHash) {
        return passwordHash != null ? Password.fromHash(passwordHash) : null;
    }
    
    protected Email mapEmailToDomain(String email) {
        return email != null ? new Email(email) : null;
    }
    
    protected Phone mapPhoneToDomain(String phone) {
        return phone != null ? new Phone(phone) : null;
    }
    
    protected DateOfBirth mapDateOfBirthToDomain(LocalDate dateOfBirth) {
        if (dateOfBirth == null) return null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        return new DateOfBirth(dateOfBirth.format(formatter));
    }
    
    protected Avatar mapAvatarToDomain(String avatar) {
        return avatar != null ? new Avatar(avatar) : null;
    }
    
    protected RefreshToken mapRefreshTokenToDomain(String token, LocalDateTime expiry) {
        if (token == null || expiry == null) return null;
        return new RefreshToken(token, expiry);
    }
    
    protected Role mapRoleToDomain(UserEntity.Role role) {
        if (role == null) return Role.USER;
        return switch (role) {
            case ADMIN -> Role.ADMIN;
            case USER -> Role.USER;
        };
    }
    
    // After mapping callback
    @AfterMapping
    protected void afterMappingToDomain(@MappingTarget User user, UserEntity entity) {
        if (user != null && entity != null) {
            user.setId(entity.getId());
            user.setUsername(entity.getUsername());
            user.setFullName(entity.getFullName());
            user.setCoin(entity.getCoin());
            user.setRate(entity.getRate());
            user.setCreatedAt(entity.getCreatedAt());
            user.setUpdatedAt(entity.getUpdatedAt());
            user.setActive(entity.isActive());
        }
    }
    
    @AfterMapping
    protected void afterMappingToEntity(@MappingTarget UserEntity entity, User domain) {
        if (entity != null && domain != null) {
            entity.setId(domain.getId());
            entity.setUsername(domain.getUsername());
            entity.setFullName(domain.getFullName());
            entity.setCoin(domain.getCoin());
            entity.setRate(domain.getRate());
            entity.setCreatedAt(domain.getCreatedAt());
            entity.setUpdatedAt(domain.getUpdatedAt());
            entity.setActive(domain.isActive());
        }
    }
}