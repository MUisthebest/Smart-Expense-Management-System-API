package com.example.smartmoney.application.ports.chat;

import com.example.smartmoney.application.dtos.requests.chat.ChatMessageDto;

import java.util.List;
import java.util.UUID;

/**
 * Port for persisting per-user chat history in Redis.
 * Key: chat:history:{userId} — TTL 30 min, max 10 turns.
 */
public interface ChatHistoryPort {

    /** Load the current history for a user (oldest first). Returns empty list if none. */
    List<ChatMessageDto> load(UUID userId);

    /**
     * Persist the updated history.
     * Caller is responsible for trimming to max 10 turns before calling.
     */
    void save(UUID userId, List<ChatMessageDto> history);
}
