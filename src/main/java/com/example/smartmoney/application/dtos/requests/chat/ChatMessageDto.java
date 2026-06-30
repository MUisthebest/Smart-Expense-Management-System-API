// Path: src/main/java/com/example/smartmoney/application/dtos/requests/chat/ChatMessageDto.java

package com.example.smartmoney.application.dtos.requests.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One turn in a chatbot conversation, as sent by the frontend inside {@link ChatRequest#getHistory()}.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    public enum Role { USER, ASSISTANT }

    private Role role;
    private String content;

    public static ChatMessageDto user(String content) {
        return new ChatMessageDto(Role.USER, content);
    }

    public static ChatMessageDto assistant(String content) {
        return new ChatMessageDto(Role.ASSISTANT, content);
    }

    /**
     * Formats this turn into a single prompt line for multi-turn Gemini prompts.
     * Example: {@code "User: I want to cut food spending this month"}
     */
    public String toPromptLine() {
        return (role == Role.USER ? "User" : "Assistant") + ": " + content;
    }
}