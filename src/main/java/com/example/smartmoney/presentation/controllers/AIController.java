package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.usecases.queue.ProcessQueueRequestUseCase;
import com.example.smartmoney.application.ports.event.EventConsumerPort;
import com.example.smartmoney.application.dtos.responses.JobResponse;
import com.example.smartmoney.domain.models.message.MessageRecord;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.Optional;
import java.util.concurrent.ForkJoinPool;

@RestController
@RequestMapping("/ai")
public class AIController {

    private final ProcessQueueRequestUseCase processQueueRequestUseCase;
    private final EventConsumerPort eventConsumerPort;

    private static final long TIMEOUT = 30_000;
    private static final long POLL_INTERVAL = 3_000;

    public AIController(
            ProcessQueueRequestUseCase processQueueRequestUseCase,
            EventConsumerPort eventConsumerPort) {

        this.processQueueRequestUseCase = processQueueRequestUseCase;
        this.eventConsumerPort = eventConsumerPort;
    }

    @PostMapping
    public DeferredResult<ResponseEntity<?>> submitAIJob(
            @RequestBody Object payload,
            @RequestHeader("tokenId") String tokenId) {

        // gửi job xuống backend
        MessageRecord record = processQueueRequestUseCase.execute(payload, tokenId);

        String jobId = record.getJobId();

        DeferredResult<ResponseEntity<?>> deferredResult = new DeferredResult<>(TIMEOUT);

        // timeout handler
        deferredResult.onTimeout(() ->
                deferredResult.setResult(
                        ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                                .body("AI job timeout after 30 seconds")
                )
        );

        // async polling Redis
        ForkJoinPool.commonPool().submit(() -> {

            long waited = 0;

            while (waited < TIMEOUT) {

                Optional<JobResponse<?>> result =
                        eventConsumerPort.consume(jobId);

                if (result.isPresent()) {

                    deferredResult.setResult(
                            ResponseEntity.status(HttpStatus.CREATED)
                                    .body(result.get())
                    );

                    return;
                }

                try {
                    Thread.sleep(POLL_INTERVAL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                waited += POLL_INTERVAL;
            }

        });

        return deferredResult;
    }
}