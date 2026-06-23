package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.ports.chat.AIChatPort;
import com.example.smartmoney.application.services.UserFinancialDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetAISuggestionsUseCase {

    private final UserFinancialDataService dataProvider;
    private final AIChatPort aiChatPort;

    public List<String> execute(UUID userId) {
        // Lấy tất cả loại dữ liệu
        String userData = dataProvider.getUserDataSummary(userId, EnumSet.allOf(UserFinancialDataService.DataType.class));
        return aiChatPort.generateSuggestions(userData, userId.toString());
    }
}