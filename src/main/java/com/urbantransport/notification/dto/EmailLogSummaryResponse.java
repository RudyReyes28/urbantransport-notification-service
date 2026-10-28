package com.urbantransport.notification.dto;

import com.urbantransport.notification.domain.EmailLogEntity;
import com.urbantransport.notification.domain.EmailStatus;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
public class EmailLogSummaryResponse {
    UUID id;
    String eventType;
    UUID referenceId;
    String recipient;
    String template;
    String subject;
    EmailStatus status;
    int attempts;
    String lastError;
    Instant nextAttemptAt;
    Instant createdAt;
    Instant sentAt;

    public static EmailLogSummaryResponse fromEntity(EmailLogEntity entity) {
        return new EmailLogSummaryResponse(entity.getId(), entity.getEventType(), entity.getReferenceId(),
                entity.getRecipient(), entity.getTemplate(), entity.getSubject(), entity.getStatus(),
                entity.getAttempts(), entity.getLastError(), entity.getNextAttemptAt(), entity.getCreatedAt(),
                entity.getSentAt());
    }
}