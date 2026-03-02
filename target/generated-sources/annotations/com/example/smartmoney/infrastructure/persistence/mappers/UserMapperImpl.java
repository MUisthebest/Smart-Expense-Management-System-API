package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Password;
import com.example.smartmoney.infrastructure.persistence.entities.UserEntity;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-03-02T17:25:13+0700",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.17 (Eclipse Adoptium)"
)
@Component
public class UserMapperImpl extends UserMapper {

    @Override
    public UserEntity toEntity(User domain) {
        if ( domain == null ) {
            return null;
        }

        UserEntity userEntity = new UserEntity();

        userEntity.setPasswordHash( domainPasswordHash( domain ) );
        userEntity.setId( domain.getId() );
        userEntity.setUsername( domain.getUsername() );
        userEntity.setFullName( domain.getFullName() );
        userEntity.setCoin( domain.getCoin() );
        userEntity.setRate( domain.getRate() );
        userEntity.setCreatedAt( domain.getCreatedAt() );
        userEntity.setUpdatedAt( domain.getUpdatedAt() );
        userEntity.setActive( domain.isActive() );

        userEntity.setEmail( mapEmailToEntity(domain.getEmail()) );
        userEntity.setPhone( mapPhoneToEntity(domain.getPhone()) );
        userEntity.setDateOfBirth( mapDateOfBirthToEntity(domain.getDateOfBirth()) );
        userEntity.setAvatar( mapAvatarToEntity(domain.getAvatar()) );
        userEntity.setRefreshToken( mapRefreshTokenToEntity(domain.getRefreshToken()) );
        userEntity.setRefreshTokenExpiry( mapRefreshTokenExpiryToEntity(domain.getRefreshToken()) );
        userEntity.setRole( mapRoleToEntity(domain.getRole()) );

        afterMappingToEntity( userEntity, domain );

        return userEntity;
    }

    @Override
    public User toDomain(UserEntity entity) {
        if ( entity == null ) {
            return null;
        }

        User.UserBuilder user = User.builder();

        user.id( entity.getId() );
        user.username( entity.getUsername() );
        user.fullName( entity.getFullName() );
        user.coin( entity.getCoin() );
        user.rate( entity.getRate() );
        user.createdAt( entity.getCreatedAt() );
        user.updatedAt( entity.getUpdatedAt() );
        user.active( entity.isActive() );

        user.password( mapPasswordToDomain(entity.getPasswordHash()) );
        user.email( mapEmailToDomain(entity.getEmail()) );
        user.phone( mapPhoneToDomain(entity.getPhone()) );
        user.dateOfBirth( mapDateOfBirthToDomain(entity.getDateOfBirth()) );
        user.avatar( mapAvatarToDomain(entity.getAvatar()) );
        user.refreshToken( mapRefreshTokenToDomain(entity.getRefreshToken(), entity.getRefreshTokenExpiry()) );
        user.role( mapRoleToDomain(entity.getRole()) );

        return user.build();
    }

    private String domainPasswordHash(User user) {
        if ( user == null ) {
            return null;
        }
        Password password = user.getPassword();
        if ( password == null ) {
            return null;
        }
        String hash = password.getHash();
        if ( hash == null ) {
            return null;
        }
        return hash;
    }
}
