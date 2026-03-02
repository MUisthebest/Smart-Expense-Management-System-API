package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.otp.OTP;
import com.example.smartmoney.domain.models.otp.enums.Type;
import com.example.smartmoney.infrastructure.persistence.entities.OTPEntity;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-03-02T17:25:13+0700",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.17 (Eclipse Adoptium)"
)
@Component
public class OTPMapperImpl extends OTPMapper {

    @Override
    public OTPEntity toEntity(OTP domain) {
        if ( domain == null ) {
            return null;
        }

        OTPEntity oTPEntity = new OTPEntity();

        oTPEntity.setId( domain.getId() );
        oTPEntity.setOtp( domain.getOtp() );
        oTPEntity.setExpiredAt( domain.getExpiredAt() );
        oTPEntity.setVerified( domain.isVerified() );
        oTPEntity.setType( typeToType( domain.getType() ) );

        oTPEntity.setEmail( mapEmailToEntity(domain.getEmail()) );

        return oTPEntity;
    }

    @Override
    public OTP toDomain(OTPEntity entity) {
        if ( entity == null ) {
            return null;
        }

        OTP.OTPBuilder oTP = OTP.builder();

        oTP.id( entity.getId() );
        oTP.otp( entity.getOtp() );
        oTP.expiredAt( entity.getExpiredAt() );
        oTP.verified( entity.isVerified() );
        oTP.type( typeToType1( entity.getType() ) );

        oTP.email( mapEmailToDomain(entity.getEmail()) );

        return oTP.build();
    }

    protected OTPEntity.Type typeToType(Type type) {
        if ( type == null ) {
            return null;
        }

        OTPEntity.Type type1;

        switch ( type ) {
            case VERIFY: type1 = OTPEntity.Type.VERIFY;
            break;
            case UPDATE: type1 = OTPEntity.Type.UPDATE;
            break;
            default: throw new IllegalArgumentException( "Unexpected enum constant: " + type );
        }

        return type1;
    }

    protected Type typeToType1(OTPEntity.Type type) {
        if ( type == null ) {
            return null;
        }

        Type type1;

        switch ( type ) {
            case VERIFY: type1 = Type.VERIFY;
            break;
            case UPDATE: type1 = Type.UPDATE;
            break;
            default: throw new IllegalArgumentException( "Unexpected enum constant: " + type );
        }

        return type1;
    }
}
