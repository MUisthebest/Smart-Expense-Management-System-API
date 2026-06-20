package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.usecases.queue.ProcessQueueRequestUseCase;
import com.example.smartmoney.application.usecases.chat.ChatWithAIUseCase;
import com.example.smartmoney.domain.exceptions.DuplicateJobException;
import com.example.smartmoney.domain.models.message.MessageRecord;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "AI-powered financial advice endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AIController {

    private final TransactionController transactionController;
    private final ProcessQueueRequestUseCase processQueueRequestUseCase;
    private final ChatWithAIUseCase chatWithAIUseCase;

    // ── Legacy queue endpoint ─────────────────────────────────────────────────

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
                            "tokenId", requested.getTokenId()
                    )
            );

        }
        catch (DuplicateJobException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                            "error", e.getMessage()
                    )
            );
        }

        catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                            "error", e.getMessage()
                    )
            );
        }
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<?> getAIResult(
            @PathVariable String jobId,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            UUID userId = transactionController.extractUserIdFromToken(authHeader);

            Map<String, Object> result = processQueueRequestUseCase.getResult(jobId, userId.toString());

            if (result == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "Job not found"
                ));
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

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader
    ) {
        try {
            UUID userId = transactionController.extractUserIdFromToken(authHeader);

            String message = body.get("message");

            String reply = chatWithAIUseCase.execute(message, userId.toString());

            return ResponseEntity.ok(Map.of(
                    "reply", reply
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}