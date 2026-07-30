package com.example.smartmoney.application.dtos.requests.chat;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    /**
     * {@code pendingActionId} returned by a previous {@code POST /api/v1/ai/chat} turn.
     *
     * <p>BUG: {@code ChatResponse} names this value {@code pendingActionId} on the way out while
     * this request named it {@code actionId} on the way back in. A client that echoed the field
     * it was actually given — the natural thing to do, and what this javadoc has always told it
     * to do — bound nothing, leaving {@code actionId} null: the confirm silently resolved no
     * action and the mutation (e.g. a chat-created project) never happened, even though the
     * preceding chat turn's prose already read as if it had. Accept the response's own field name
     * as an alias so both spellings work.
     */
    @JsonAlias("pendingActionId")
    private String actionId;

    /** True to execute the action; false discards it without executing. */
    private boolean confirm;
}
