package com.example.smartmoney.application.usecases.suggestion;

import com.example.smartmoney.application.dtos.responses.suggestion.SuggestionResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.suggestion.SuggestionStatus;
import com.example.smartmoney.domain.repositories.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Lists a user's suggestions by status (defaults to PENDING) for the in-app card screen. */
@Service
@RequiredArgsConstructor
public class ListSuggestionsUseCase {

    private final SuggestionRepository suggestionRepository;
    private final SuggestionResponseAssembler assembler;

    @Transactional(readOnly = true)
    public List<SuggestionResponse> execute(UUID userId, SuggestionStatus status) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }
        SuggestionStatus effective = status != null ? status : SuggestionStatus.PENDING;
        return suggestionRepository.findByUserIdAndStatus(userId, effective).stream()
                .map(assembler::toResponse)
                .toList();
    }
}
