package com.example.smartmoney.application.dtos.requests.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for {@code POST /api/v1/ai/chat/confirm}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmChatActionRequest {

    /** {@code pendingActionId} returned by a previous {@code POST /api/v1/ai/chat} turn. */
    private String actionId;

    /** True to execute the action; false discards it without executing. */
    private boolean confirm;
}
