// Path: src/main/java/com/example/smartmoney/application/dtos/requests/chat/ChatMessageDto.java

package com.example.smartmoney.application.dtos.requests.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One turn in a chatbot conversation stored in Redis history. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    /** SYSTEM holds the rolling compressed-history summary (see ChatHistoryCacheService). */
    public enum Role { USER, ASSISTANT, SYSTEM }

    private Role role;
    private String content;

    public static ChatMessageDto user(String content) {
        return new ChatMessageDto(Role.USER, content);
    }

    public static ChatMessageDto assistant(String content) {
        return new ChatMessageDto(Role.ASSISTANT, content);
    }

    public static ChatMessageDto summary(String content) {
        return new ChatMessageDto(Role.SYSTEM, content);
    }

    /**
     * Formats this turn into a single prompt line for multi-turn Gemini prompts.
     * Example: {@code "User: I want to cut food spending this month"}
     * A SYSTEM entry is the rolling summary and is already self-labelled ("[SUMMARY] ..."),
     * so it's emitted verbatim with no extra prefix.
     */
    public String toPromptLine() {
        return switch (role) {
            case USER -> "User: " + content;
            case ASSISTANT -> "Assistant: " + content;
            case SYSTEM -> content;
        };
    }
}