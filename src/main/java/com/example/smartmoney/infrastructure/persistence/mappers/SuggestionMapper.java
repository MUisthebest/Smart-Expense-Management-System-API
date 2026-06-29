package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.infrastructure.persistence.entities.SuggestionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.springframework.stereotype.Component;

@Component
@Mapper(componentModel = "spring")
public abstract class SuggestionMapper {

    public abstract SuggestionEntity toEntity(Suggestion domain);

    public abstract Suggestion toDomain(SuggestionEntity entity);

    @org.mapstruct.AfterMapping
    protected void afterMappingToEntity(@MappingTarget SuggestionEntity entity, Suggestion domain) {
        if (domain != null && entity != null) {
            entity.setId(domain.getId());
            entity.setUserId(domain.getUserId());
            entity.setType(domain.getType());
            entity.setStatus(domain.getStatus());
            entity.setPayload(domain.getPayloadJson());
            entity.setDedupKey(domain.getDedupKey());
            entity.setCreatedAt(domain.getCreatedAt());
            entity.setExpiresAt(domain.getExpiresAt());
            entity.setDecidedAt(domain.getDecidedAt());
        }
    }

    @org.mapstruct.AfterMapping
    protected void afterMappingToDomain(@MappingTarget Suggestion domain, SuggestionEntity entity) {
        if (domain != null && entity != null) {
            domain.setId(entity.getId());
            domain.setUserId(entity.getUserId());
            domain.setType(entity.getType());
            domain.setStatus(entity.getStatus());
            domain.setPayloadJson(entity.getPayload());
            domain.setDedupKey(entity.getDedupKey());
            domain.setCreatedAt(entity.getCreatedAt());
            domain.setExpiresAt(entity.getExpiresAt());
            domain.setDecidedAt(entity.getDecidedAt());
        }
    }
}
