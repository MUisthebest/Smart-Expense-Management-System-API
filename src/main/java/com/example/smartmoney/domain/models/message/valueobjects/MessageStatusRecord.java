package com.example.smartmoney.domain.models.message.valueobjects;
import com.example.smartmoney.domain.models.message.enums.MessageStatus;  

/**
 * Initialize the essential fields for the MessageRecord class,
 * which represents a record of a message in the queue.
 */
public class MessageStatusRecord {

    private final MessageStatus status;

    public MessageStatusRecord() {
        this.status = MessageStatus.PENDING; // Default status when a message is created
    }

    public MessageStatusRecord (MessageStatus status) {
        this.status = status;
    }

    public MessageStatus getStatus() {
        return status;
    }
}