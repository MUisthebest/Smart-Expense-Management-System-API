package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.chat.ChatRequest;
import com.example.smartmoney.application.dtos.requests.chat.ConfirmChatActionRequest;
import com.example.smartmoney.application.dtos.responses.ChatActionConfirmResponse;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.guardrails.GuardrailViolationException;
import com.example.smartmoney.application.guardrails.RateLimitExceededException;
import com.example.smartmoney.application.usecases.queue.ProcessQueueRequestUseCase;
import com.example.smartmoney.application.usecases.chat.ChatWithAIUseCase;
import com.example.smartmoney.application.usecases.chat.ConfirmChatActionUseCase;
import com.example.smartmoney.application.usecases.chat.GetAISuggestionsUseCase;
import com.example.smartmoney.domain.exceptions.DuplicateJobException;
import com.example.smartmoney.domain.models.message.MessageRecord;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for all AI / chatbot endpoints.
 *
 * POST /chat  — sync, structured ChatResponse. History managed server-side in Redis.
 * GET /chat/stream — SSE, cumulative reply tokens, ends event:done data:[DONE].
 * GET /suggestions — returns {questions: string[]}, 3–5 chips.
 * POST / and GET /{jobId} — legacy queue endpoints, unchanged.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI-powered financial advice endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AIController {

    private final TransactionController transactionController;
    private final ProcessQueueRequestUseCase processQueueRequestUseCase;
    private final ChatWithAIUseCase chatWithAIUseCase;
    private final ConfirmChatActionUseCase confirmChatActionUseCase;
    private final GetAISuggestionsUseCase getAISuggestionsUseCase;

    // ── Legacy queue endpoints (unchanged) ───────────────────────────────────

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submitAIJob(
            @RequestPart("data") String data,
            @RequestPart("type") String duty,
            @RequestHeader("Authorization") String authHeader) {
        try {
            UUID userId = transactionController.extractUserIdFromToken(authHeader);
            MessageRecord requested = processQueueRequestUseCase.execute(data, duty, userId.toString());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "jobId", requested.getJobId(),
                    "status", requested.getStatus(),
                    "tokenId", requested.getTokenId()));

        } catch (DuplicateJobException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<?> getAIResult(
            @PathVariable String jobId,
            @RequestHeader("Authorization") String authHeader) {
        try {
            UUID userId = transactionController.extractUserIdFromToken(authHeader);
            Map<String, Object> result = processQueueRequestUseCase.getResult(jobId, userId.toString());

            if (result == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
            }

            Map<String, Object> response = new HashMap<>();

            if (result.get("jobId") != null)
                response.put("jobId", result.get("jobId"));

            if (result.get("text") != null)
                response.put("text", result.get("text"));

            if (result.get("expense") != null)
                response.put("expense", result.get("expense"));

            if (result.get("type") != null)
                response.put("type", result.get("type"));

            if (result.get("category") != null)
                response.put("category", result.get("category"));

            if (result.get("confidence") != null)
                response.put("confidence", result.get("confidence"));

            if (result.get("userId") != null)
                response.put("userId", result.get("userId"));

            if (result.get("transactions") != null)
                response.put("transactions", result.get("transactions"));
            if (result.get("status") != null)
                response.put("status", result.get("status"));

            if (result.get("result") != null)
                response.put("result", result.get("result"));

            if (result.get("error") != null)
                response.put("error", result.get("error"));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Chat endpoints (UC1 / UC2 / UC3) ────────────────────────────────────

    /**
     * Sync chat. Body: {message, month?, year?}. History from Redis.
     * UC1→BUDGET_UPDATE, UC2→SIMULATION, UC3→personalised tone, guardrails applied.
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            @RequestBody ChatRequest chatRequest,
            @RequestHeader("Authorization") String authHeader) {
        try {
            UUID userId = transactionController.extractUserIdFromToken(authHeader);
            ChatResponse response = chatWithAIUseCase.execute(chatRequest, userId);
            return ResponseEntity.ok(response);

        } catch (RateLimitExceededException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", e.getMessage()));

        } catch (GuardrailViolationException e) {
            String reply = e.getMessage() != null ? e.getMessage()
                    : "Tôi chỉ hỗ trợ các câu hỏi về tài chính cá nhân như ngân sách, " +
                      "chi tiêu, và mục tiêu tiết kiệm. Bạn muốn hỏi gì về tài chính không?";
            return ResponseEntity.ok(ChatResponse.builder()
                    .intent(ChatResponse.ChatIntent.OUT_OF_SCOPE)
                    .reply(reply)
                    .build());

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(),
                                 "detail", cause.getMessage() != null ? cause.getMessage() : ""));
        }
    }

    /**
     * Confirm (or deny) a {@code pendingActionId} returned by a previous {@code POST /chat} turn
     * (a project-hypothesis or budget-increase-swap suggestion). Only on {@code confirm: true}
     * does the backend execute the real mutation — see {@link ConfirmChatActionUseCase}.
     */
    @PostMapping("/chat/confirm")
    public ResponseEntity<?> confirmChatAction(
            @RequestBody ConfirmChatActionRequest request,
            @RequestHeader("Authorization") String authHeader) {
        try {
            UUID userId = transactionController.extractUserIdFromToken(authHeader);
            ChatActionConfirmResponse response = confirmChatActionUseCase.execute(userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── Suggestions (unchanged) ──────────────────────────────────────────────

    @GetMapping("/suggestions")
    public ResponseEntity<?> getSuggestions(@RequestHeader("Authorization") String authHeader) {
        try {
            UUID userId = transactionController.extractUserIdFromToken(authHeader);
            List<String> questions = getAISuggestionsUseCase.execute(userId);
            return ResponseEntity.ok(Map.of("questions", questions));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── Streaming chat (UC3 personalisation + UC1/UC2 context) ──────────────

    /**
     * SSE streaming chat endpoint.
     *
     * SSE stream. Tokens cumulative. Ends event:done data:[DONE].
     * No structured payloads — use POST /chat for those.
     * History loaded from Redis before stream; ASSISTANT turn appended after completion.
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam String message,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestHeader("Authorization") String authHeader) {

        SseEmitter emitter = new SseEmitter(120_000L);

        try {
            UUID userId = transactionController.extractUserIdFromToken(authHeader);

            ChatRequest chatRequest = ChatRequest.builder()
                    .message(message)
                    .month(month)
                    .year(year)
                    .build();

            chatWithAIUseCase.streamChat(
                    chatRequest,
                    userId,
                    chunk -> {
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    },
                    error -> {
                        // Same rationale as the sync /chat fallback: a network blip or an
                        // overloaded/rate-limited Claude call shouldn't surface as a raw stream
                        // error the client can't render — send a friendly reply and end the
                        // stream normally instead.
                        log.warn("streamChat: LLM stream failed: {}", error.getMessage());
                        sendReplyThenDone(emitter, "Xin lỗi, trợ lý AI đang gặp sự cố kết nối tạm thời. " +
                                "Bạn vui lòng thử lại sau ít phút nhé.");
                    },
                    () -> {
                        try {
                            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                        } catch (IOException ignored) {
                        }
                        emitter.complete();
                    });

        } catch (RateLimitExceededException e) {
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"error\":\"rate_limit\"}"));
            } catch (IOException ignored) {
            }
            emitter.complete();

        } catch (GuardrailViolationException e) {
            String reply = e.getMessage() != null ? e.getMessage()
                    : "Tôi chỉ hỗ trợ câu hỏi về tài chính cá nhân.";
            sendReplyThenDone(emitter, reply);

        } catch (Exception e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /** Sends one text chunk followed by the {@code done} terminator, then completes normally. */
    private void sendReplyThenDone(SseEmitter emitter, String reply) {
        try {
            emitter.send(SseEmitter.event().data(reply));
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
        } catch (IOException ignored) {
        }
        emitter.complete();
    }
}
