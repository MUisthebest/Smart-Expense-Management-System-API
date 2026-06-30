package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.chat.ChatRequest;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.usecases.queue.ProcessQueueRequestUseCase;
import com.example.smartmoney.application.usecases.chat.ChatWithAIUseCase;
import com.example.smartmoney.application.usecases.chat.GetAISuggestionsUseCase;
import com.example.smartmoney.domain.exceptions.DuplicateJobException;
import com.example.smartmoney.domain.models.message.MessageRecord;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
 * UC1 / UC2 / UC3 changes:
 *   POST /chat now accepts a full {@link ChatRequest} (message + history + month + year)
 *   and returns a structured {@link ChatResponse} with intent classification and optional
 *   BudgetUpdateSuggestion or SimulationResult payloads.
 *
 *   GET /chat/stream applies the same UC3 personalisation and UC1/UC2 context logic;
 *   it accepts optional {@code month} and {@code year} query params.
 *
 * The legacy queue endpoints (POST / and GET /{jobId}) are unchanged.
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI-powered financial advice endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AIController {

    private final TransactionController transactionController;
    private final ProcessQueueRequestUseCase processQueueRequestUseCase;
    private final ChatWithAIUseCase chatWithAIUseCase;
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
            if (result.get("jobId")      != null) response.put("jobId",      result.get("jobId"));
            if (result.get("text")       != null) response.put("text",       result.get("text"));
            if (result.get("expense")    != null) response.put("expense",    result.get("expense"));
            if (result.get("type")       != null) response.put("type",       result.get("type"));
            if (result.get("category")   != null) response.put("category",   result.get("category"));
            if (result.get("confidence") != null) response.put("confidence", result.get("confidence"));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Chat endpoints (UC1 / UC2 / UC3) ────────────────────────────────────

    /**
     * Synchronous chat — returns a structured ChatResponse.
     *
     * Old behaviour (plain reply):
     * -------------------------------------------------------------------------
     * // @PostMapping("/chat")
     * // public ResponseEntity<?> chat(
     * //         @RequestBody Map<String, String> body,
     * //         @RequestHeader("Authorization") String authHeader) {
     * //     UUID userId = transactionController.extractUserIdFromToken(authHeader);
     * //     String message = body.get("message");
     * //     String reply = chatWithAIUseCase.execute(message, userId);
     * //     return ResponseEntity.ok(Map.of("reply", reply));
     * // }
     * -------------------------------------------------------------------------
     *
     * New behaviour:
     *  - Accepts a full {@link ChatRequest} body (message, history, month, year).
     *  - Returns a {@link ChatResponse} with intent + optional structured payloads.
     *  - UC1: intent=BUDGET_UPDATE + budgetSuggestions list (frontend calls PUT /budgets/{id})
     *  - UC2: intent=SIMULATION    + simulationResult
     *  - UC3: reply tone shaped by user's savingPace / interventionLevel / focusMode
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            @RequestBody ChatRequest chatRequest,
            @RequestHeader("Authorization") String authHeader) {
        try {
            UUID userId = transactionController.extractUserIdFromToken(authHeader);

            // UC1 / UC2 / UC3: delegate to the refactored use case
            ChatResponse response = chatWithAIUseCase.execute(chatRequest, userId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage(), "detail", cause.getMessage() != null ? cause.getMessage() : ""));
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
     * Old behaviour:
     * -------------------------------------------------------------------------
     * // @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
     * // public SseEmitter streamChat(
     * //         @RequestParam String message,
     * //         @RequestHeader("Authorization") String authHeader) {
     * //     UUID userId = transactionController.extractUserIdFromToken(authHeader);
     * //     SseEmitter emitter = new SseEmitter(120_000L);
     * //     chatWithAIUseCase.streamChat(message, userId, chunk -> {
     * //         try { emitter.send(SseEmitter.event().data(chunk)); }
     * //         catch (IOException e) { emitter.completeWithError(e); }
     * //     }, error -> emitter.completeWithError(error),
     * //     () -> { try { emitter.send(...); } catch ... emitter.complete(); emitter.complete(); });
     * //     return emitter;
     * // }
     * -------------------------------------------------------------------------
     *
     * New behaviour:
     *  - Accepts optional {@code month} and {@code year} to scope financial context.
     *  - Builds a {@link ChatRequest} and delegates to the refactored streamChat().
     *  - UC3 personalisation (tone) is applied via the system prompt.
     *  - UC1/UC2 context (budgets or simulation) is injected ahead of the user message.
     *  - Structured BudgetUpdateSuggestion / SimulationResult are NOT sent via SSE;
     *    use POST /chat for those payloads.
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam String message,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestHeader("Authorization") String authHeader) {

        UUID userId = transactionController.extractUserIdFromToken(authHeader);

        // Build a minimal ChatRequest from query params (no history in stream mode)
        ChatRequest chatRequest = ChatRequest.builder()
                .message(message)
                .month(month)
                .year(year)
                .build();

        SseEmitter emitter = new SseEmitter(120_000L);

        chatWithAIUseCase.streamChat(
                chatRequest,
                userId,
                chunk -> {
                    try {
                        // Each call delivers the CUMULATIVE reply text (not just the delta),
                        // so the frontend can do a simple replacement instead of concatenation
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                error -> emitter.completeWithError(error),
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    } catch (IOException ignored) {
                    }
                    emitter.complete();
                });

        return emitter;
    }
}
