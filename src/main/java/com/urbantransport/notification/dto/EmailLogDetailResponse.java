package com.urbantransport.notification.dto;

import com.urbantransport.notification.domain.EmailLogEntity;
import com.urbantransport.notification.domain.EmailStatus;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
public class EmailLogDetailResponse {
    UUID id;
    UUID eventId;
    String eventType;
    UUID referenceId;
    String recipient;
    String template;
    String subject;
    String htmlBody;
    EmailStatus status;
    int attempts;
    String lastError;
    Instant nextAttemptAt;
    Instant createdAt;
    Instant sentAt;

    public static EmailLogDetailResponse fromEntity(EmailLogEntity entity) {
        return new EmailLogDetailResponse(entity.getId(), entity.getEventId(), entity.getEventType(),
                entity.getReferenceId(), entity.getRecipient(), entity.getTemplate(), entity.getSubject(),
                entity.getBody(), entity.getStatus(), entity.getAttempts(), entity.getLastError(),
                entity.getNextAttemptAt(), entity.getCreatedAt(), entity.getSentAt());
    }
}