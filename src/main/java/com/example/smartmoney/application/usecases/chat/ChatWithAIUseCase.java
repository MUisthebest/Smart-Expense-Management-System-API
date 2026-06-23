package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.ports.chat.AIChatPort;
import com.example.smartmoney.application.services.UserFinancialDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class ChatWithAIUseCase {

    private final UserFinancialDataService dataProvider;
    
    private final AIChatPort aiChatPort;

    public String execute(String message, UUID userId) {
        // 1. Xác định loại dữ liệu cần lấy dựa trên nội dung tin nhắn
        Set<UserFinancialDataService.DataType> dataTypes = determineDataTypes(message);

        // 2. Lấy dữ liệu tài chính của người dùng (chỉ những loại cần thiết)
        String userData = dataProvider.getUserDataSummary(userId, dataTypes);

        // 3. Xây dựng prompt hoàn chỉnh
        String fullPrompt;
        if (userData.isEmpty()) {
            fullPrompt = "Câu hỏi của bạn: " + message;
        } else {
            fullPrompt = "Đây là dữ liệu tài chính của bạn:\n" + userData + "\n\nCâu hỏi của bạn: " + message;
        }

        // 4. Gọi AI
        return aiChatPort.chat(fullPrompt, userId.toString());
    }

    // Method mới cho streaming
    public void streamChat(String message, UUID userId, 
                           Consumer<String> onNext, 
                           Consumer<Throwable> onError, 
                           Runnable onComplete) {
        // 1. Xác định loại dữ liệu cần lấy
        Set<UserFinancialDataService.DataType> dataTypes = determineDataTypes(message);
        
        // 2. Lấy dữ liệu tài chính (phần này nhanh, không cần async)
        String userData = dataProvider.getUserDataSummary(userId, dataTypes);

        // 3. Xây dựng prompt hoàn chỉnh
        String fullPrompt;
        if (userData.isEmpty()) {
            fullPrompt = "Câu hỏi của bạn: " + message;
        } else {
            fullPrompt = "Đây là dữ liệu tài chính của bạn:\n" + userData + "\n\nCâu hỏi của bạn: " + message;
        }

        // 4. Gọi AI với cơ chế stream
        aiChatPort.chatStream(fullPrompt, userId.toString(), onNext, onError, onComplete);
    }

    /**
     * Phân tích tin nhắn để quyết định lấy những loại dữ liệu nào.
     * Mặc định luôn lấy INCOME và PROFILE vì hữu ích cho hầu hết câu hỏi.
     */
    private Set<UserFinancialDataService.DataType> determineDataTypes(String message) {
        Set<UserFinancialDataService.DataType> types = new HashSet<>();
        // Luôn lấy thông tin cơ bản
        types.add(UserFinancialDataService.DataType.INCOME);

        String lower = message.toLowerCase();
        if (lower.contains("chi tiêu") || lower.contains("giao dịch") || lower.contains("transaction") ||
            lower.contains("expense") || lower.contains("thu nhập") || lower.contains("income") ||
            lower.contains("ngân sách") || lower.contains("budget")) {
            types.add(UserFinancialDataService.DataType.TRANSACTIONS);
        }
        if (lower.contains("dự án") || lower.contains("project") || lower.contains("mục tiêu") || lower.contains("goal")) {
            types.add(UserFinancialDataService.DataType.PROJECTS);
        }
        return types;
    }
}