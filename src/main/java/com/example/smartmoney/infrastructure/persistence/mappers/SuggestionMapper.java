package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.infrastructure.persistence.entities.SuggestionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps between the {@link Suggestion} domain model and its JPA entity. The only field whose name
 * differs between the two sides is the JSON payload ({@code payloadJson} ↔ column {@code payload}),
 * so it is mapped explicitly. Every other field matches by name.
 *
 * <p><b>Why explicit {@code @Mapping} and not {@code @AfterMapping}:</b> the domain model is built
 * through a Lombok {@code @Builder}, and MapStruct does not invoke an {@code @AfterMapping} method
 * whose {@code @MappingTarget} is the built type on the builder path — so a mismatched-name field
 * set only in an after-mapping hook would silently come back {@code null} on read. Declaring the
 * mapping directly makes MapStruct emit {@code builder.payloadJson(entity.getPayload())}.
 */
@Mapper(componentModel = "spring")
public interface SuggestionMapper {

    @Mapping(target = "payload", source = "payloadJson")
    SuggestionEntity toEntity(Suggestion domain);

    @Mapping(target = "payloadJson", source = "payload")
    Suggestion toDomain(SuggestionEntity entity);
}
