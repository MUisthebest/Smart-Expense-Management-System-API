package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.ports.chat.AIChatPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatWithAIUseCase {

    private final AIChatPort aiChatPort;

    public String execute(String message, String userId) {
        return aiChatPort.chat(message, userId);
    }
}