package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.domain.ProcessedEventEntity;
import com.urbantransport.notification.domain.Role;
import com.urbantransport.notification.domain.UserDirectoryEntity;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.event.UserEvent;
import com.urbantransport.notification.repository.ProcessedEventRepository;
import com.urbantransport.notification.repository.UserDirectoryRepository;
import com.urbantransport.notification.service.UserDirectoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * El directorio solo guarda personal. El cambio y el registro del eventId van en una sola transacción.
 * Un evento incompleto lanza excepción para que el mensaje termine en el DLT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDirectoryServiceImpl implements UserDirectoryService {

    private final UserDirectoryRepository userDirectoryRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Override
    @Transactional
    public void applyUserEvent(UserEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.debug("Evento {} ya procesado, se ignora", event.eventId());
            return;
        }
        UserEvent.UserPayload payload = event.payload();
        if (payload == null || payload.userId() == null || payload.email() == null || payload.fullName() == null
                || event.occurredAt() == null) {
            throw new IllegalArgumentException("Evento de usuario incompleto: " + event.eventId());
        }

        Role role = parseRole(payload.role());
        if (role == null || role == Role.CUSTOMER) {
            log.debug("Usuario {} con rol {} no entra al directorio", payload.userId(), payload.role());
        } else {
            upsert(event, payload, role);
        }
        processedEventRepository.save(ProcessedEventEntity.builder().eventId(event.eventId()).build());
    }

    private void upsert(UserEvent event, UserEvent.UserPayload payload, Role role) {
        UserDirectoryEntity current = userDirectoryRepository.findById(payload.userId()).orElse(null);
        Instant occurredAt = event.occurredAt();
        if (current != null && !occurredAt.isAfter(current.getLastEventAt())) {
            log.debug("Evento {} de {} es más viejo que el último aplicado, se descarta", event.eventId(), payload.userId());
            return;
        }

        boolean active = payload.active() && !EventTypes.USER_DEACTIVATED.equals(event.eventType());
        UserDirectoryEntity entity = current != null ? current : new UserDirectoryEntity();
        entity.setUserId(payload.userId());
        entity.setEmail(payload.email().trim().toLowerCase(Locale.ROOT));
        entity.setFullName(payload.fullName());
        entity.setRole(role);
        entity.setBranchId(payload.branchId());
        entity.setActive(active);
        entity.setLastEventAt(occurredAt);
        userDirectoryRepository.save(entity);
    }

    private Role parseRole(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}