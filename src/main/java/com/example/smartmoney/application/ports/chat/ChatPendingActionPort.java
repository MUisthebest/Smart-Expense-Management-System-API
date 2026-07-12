package com.example.smartmoney.application.ports.chat;

import com.example.smartmoney.application.dtos.chat.PendingChatAction;

import java.util.Optional;
import java.util.UUID;

/** Port for the confirm-then-execute pending-action store (see {@code PendingChatAction}). */
public interface ChatPendingActionPort {

    void save(UUID userId, String actionId, PendingChatAction action);

    Optional<PendingChatAction> load(UUID userId, String actionId);

    void delete(UUID userId, String actionId);

    /** Marks {@code actionId} as this user's most recent pending action, so a later plain "yes"/"no"
     *  reply (no actionId of its own) can still be resolved to the right action. */
    void saveLatestId(UUID userId, String actionId);

    Optional<String> loadLatestId(UUID userId);

    void clearLatestId(UUID userId);
}
