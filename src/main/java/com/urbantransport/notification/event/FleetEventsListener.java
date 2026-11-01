package com.urbantransport.notification.event;

import com.urbantransport.notification.service.DriverDirectoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class FleetEventsListener {

    private final JsonMapper jsonMapper;
    private final DriverDirectoryService driverDirectoryService;

    @KafkaListener(topics = "${notification.kafka.fleet-events-topic}")
    public void onEvent(String message) {
        EventHeader header = jsonMapper.readValue(message, EventHeader.class);
        String type = header.eventType() == null ? "" : header.eventType();

        switch (type) {
            case EventTypes.DRIVER_REGISTERED, EventTypes.DRIVER_UPDATED, EventTypes.DRIVER_DEACTIVATED ->
                    driverDirectoryService.applyDriverEvent(jsonMapper.readValue(message, DriverEvent.class));
            default -> log.debug("Evento {} ignorado", type);
        }
    }
}