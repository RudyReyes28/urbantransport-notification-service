package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.domain.DriverDirectoryEntity;
import com.urbantransport.notification.domain.ProcessedEventEntity;
import com.urbantransport.notification.event.DriverEvent;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.repository.DriverDirectoryRepository;
import com.urbantransport.notification.repository.ProcessedEventRepository;
import com.urbantransport.notification.service.DriverDirectoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverDirectoryServiceImpl implements DriverDirectoryService {

    private final DriverDirectoryRepository driverDirectoryRepository;
    private final ProcessedEventRepository processedEventRepository;

    @Override
    @Transactional
    public void applyDriverEvent(DriverEvent event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.debug("Evento {} ya procesado, se ignora", event.eventId());
            return;
        }
        DriverEvent.Payload payload = event.payload();
        if (payload == null || payload.driverId() == null || payload.fullName() == null || payload.email() == null
                || payload.branchId() == null || payload.licenseNumber() == null
                || payload.licenseExpiresOn() == null || event.occurredAt() == null) {
            throw new IllegalArgumentException("Evento de chofer incompleto: " + event.eventId());
        }

        DriverDirectoryEntity current = driverDirectoryRepository.findById(payload.driverId()).orElse(null);
        Instant occurredAt = event.occurredAt();
        if (current != null && !occurredAt.isAfter(current.getLastEventAt())) {
            log.debug("Evento {} del chofer {} es más viejo que el último aplicado, se descarta",
                    event.eventId(), payload.driverId());
        } else {
            DriverDirectoryEntity entity = current != null ? current : new DriverDirectoryEntity();
            entity.setDriverId(payload.driverId());
            entity.setUserId(payload.userId());
            entity.setFullName(payload.fullName());
            entity.setEmail(payload.email().trim().toLowerCase(Locale.ROOT));
            entity.setBranchId(payload.branchId());
            entity.setLicenseNumber(payload.licenseNumber());
            entity.setLicenseExpiresOn(payload.licenseExpiresOn());
            entity.setActive(payload.active() && !EventTypes.DRIVER_DEACTIVATED.equals(event.eventType()));
            entity.setLastEventAt(occurredAt);
            driverDirectoryRepository.save(entity);
        }
        processedEventRepository.save(ProcessedEventEntity.builder().eventId(event.eventId()).build());
    }
}