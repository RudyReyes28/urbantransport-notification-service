package com.urbantransport.notification.job;

import com.urbantransport.notification.config.NotificationDispatchProperties;
import com.urbantransport.notification.repository.EmailLogRepository;
import com.urbantransport.notification.service.EmailDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Cada pocos segundos envía los correos pendientes que ya les toca. fixedDelay evita que dos ciclos se solapen. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailDispatchJob {

    private final EmailLogRepository emailLogRepository;
    private final EmailDispatchService emailDispatchService;
    private final NotificationDispatchProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${notification.dispatch.poll-interval-ms:5000}")
    public void dispatchDue() {
        List<UUID> due = emailLogRepository.findDueIds(Instant.now(clock), PageRequest.of(0, properties.batchSize()));
        for (UUID id : due) {
            try {
                emailDispatchService.dispatch(id);
            } catch (RuntimeException exception) {
                log.error("No se pudo despachar el correo {}", id, exception);
            }
        }
    }
}