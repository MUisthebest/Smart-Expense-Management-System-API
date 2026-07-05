// Path: src/main/java/com/example/smartmoney/application/dtos/requests/chat/ChatRequest.java

package com.example.smartmoney.application.dtos.requests.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /api/v1/ai/chat}.
 * History is managed server-side in Redis (TTL 30 min, max 10 turns).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /** The user's current message. */
    private String message;

    /** Month to analyse (1–12). Defaults to the current month when null. */
    private Integer month;

    /** Year to analyse. Defaults to the current year when null. */
    private Integer year;
}