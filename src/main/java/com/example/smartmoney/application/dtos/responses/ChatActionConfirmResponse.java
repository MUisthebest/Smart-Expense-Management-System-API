package com.example.smartmoney.application.dtos.responses;

import lombok.Builder;
import lombok.Value;

/** Response for {@code POST /api/v1/ai/chat/confirm}. */
@Value
@Builder
public class ChatActionConfirmResponse {

    /** False when the action was denied, expired, or failed to apply. */
    boolean success;

    /** Human-readable outcome, same language convention as {@code ChatResponse.reply}. */
    String reply;

    /**
     * Fresh {@code POST /api/v1/budgets/allocation/compute} numbers after a Step 1 project/budget
     * mutation (Step 2 of the workflow) — null when denied, when the action was a pure
     * {@code PROJECT_DELETE} (step 2 is skipped for delete per spec), or on failure.
     */
    Long safeSpendingAfter;
    Long envelopeAfter;

    /** Language of the message that originally produced this action ({@link
     *  com.example.smartmoney.application.dtos.chat.PendingChatAction#isEnglish()}) — propagated
     *  so {@code ResolvePlainAnswerUseCase} can stamp the same language onto the {@code
     *  ChatResponse} it builds for a bare "yes"/"no" reply, which itself carries no language
     *  signal of its own. False (Vietnamese) when the action was already gone (not found). */
    boolean english;
}
