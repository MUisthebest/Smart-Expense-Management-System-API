package com.example.smartmoney.domain.services;

import java.time.Duration;
import java.time.LocalDateTime;

import com.example.smartmoney.domain.models.message.MessageRecord;
import com.example.smartmoney.domain.models.message.enums.MessageStatus;

public class QueueDomainService {

    // Check message is still processing within the timeout\
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    public boolean isTimeout(MessageRecord record) {

        Duration elapsed = Duration.between(record.getCreatedAt(), LocalDateTime.now());

        return elapsed.compareTo(TIMEOUT) > 0;
    }

    public boolean isStillProcessing(MessageRecord record) {

        return record.getStatus() == MessageStatus.PROCESSING
                && !isTimeout(record);
    }

    public boolean canProcessNewRequest(MessageRecord record) {

        if (record == null) {
            return true;
        }

        if (isStillProcessing(record)) {
            return false;
        }

        return true;
    }
}

