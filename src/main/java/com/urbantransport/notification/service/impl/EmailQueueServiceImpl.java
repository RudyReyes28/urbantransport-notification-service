package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.domain.EmailLogEntity;
import com.urbantransport.notification.domain.EmailStatus;
import com.urbantransport.notification.repository.EmailLogRepository;
import com.urbantransport.notification.service.EmailQueueService;
import com.urbantransport.notification.service.EmailRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailQueueServiceImpl implements EmailQueueService {

    private static final int SUBJECT_MAX = 200;
    private static final int REASON_MAX = 1000;

    private final EmailLogRepository emailLogRepository;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean enqueue(EmailRequest request) {
        if (isDuplicate(request.eventId(), request.template(), request.recipient())) {
            log.debug("Correo duplicado para el evento {} ({}), se ignora", request.eventId(), request.template());
            return false;
        }
        Instant now = Instant.now(clock);
        emailLogRepository.save(EmailLogEntity.builder()
                .id(UUID.randomUUID())
                .eventId(request.eventId())
                .eventType(request.eventType())
                .referenceId(request.referenceId())
                .recipient(request.recipient())
                .template(request.template())
                .subject(truncate(request.subject(), SUBJECT_MAX))
                .body(request.htmlBody())
                .status(EmailStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .build());
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean skip(UUID eventId, String eventType, UUID referenceId, String template, String subject,
                        String reason) {
        if (isDuplicate(eventId, template, null)) {
            return false;
        }
        emailLogRepository.save(EmailLogEntity.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .eventType(eventType)
                .referenceId(referenceId)
                .template(template)
                .subject(truncate(subject, SUBJECT_MAX))
                .status(EmailStatus.SKIPPED)
                .attempts(0)
                .lastError(truncate(reason, REASON_MAX))
                .build());
        return true;
    }

    private boolean isDuplicate(UUID eventId, String template, String recipient) {
        return emailLogRepository.existsDuplicate(eventId, template, recipient == null ? "" : recipient);
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}