// Path: src/main/java/com/example/smartmoney/application/dtos/requests/chat/ChatRequest.java

package com.example.smartmoney.application.dtos.requests.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/assistant/chat}.
 *
 * <p>The backend is stateless — the frontend stores conversation history locally
 * and sends the last N turns back on every request (recommended max: 10).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /** The user's current message. */
    private String message;

    /**
     * Previous conversation turns (oldest first).
     * Trimmed to {@code MAX_HISTORY_TURNS} inside the use case.
     */
    private List<ChatMessageDto> history;

    /**
     * Month to analyse (1–12). Defaults to the current month when null.
     */
    private Integer month;

    /**
     * Year to analyse. Defaults to the current year when null.
     */
    private Integer year;
}