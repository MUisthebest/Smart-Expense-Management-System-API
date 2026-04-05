package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.usecases.queue.ProcessQueueRequestUseCase;
import com.example.smartmoney.domain.exceptions.DuplicateJobException;
import com.example.smartmoney.domain.models.message.MessageRecord;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ai/v1")
public class AIController {
    private final TransactionController transactionController;
    private final ProcessQueueRequestUseCase processQueueRequestUseCase;
    public AIController(
            TransactionController transactionController,
            ProcessQueueRequestUseCase processQueueRequestUseCase

        )
    {
        this.transactionController = transactionController;
        this.processQueueRequestUseCase = processQueueRequestUseCase;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submitAIJob(
            @RequestPart("data") String data,
            @RequestPart("type") String duty,
            @RequestHeader("Authorization") String authHeader) {


        try {
            UUID userId = transactionController.extractUserIdFromToken(authHeader);

            System.out.println("User ID: " + userId);

            MessageRecord requested = processQueueRequestUseCase.execute(
                    data,
                    duty,
                    userId.toString()
            );
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
}