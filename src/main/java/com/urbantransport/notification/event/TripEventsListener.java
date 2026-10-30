package com.urbantransport.notification.event;

import com.urbantransport.notification.service.TripNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripEventsListener {

    private final JsonMapper jsonMapper;
    private final TripNotificationService tripNotificationService;

    @KafkaListener(topics = "${notification.kafka.trip-events-topic}")
    public void onEvent(String message) {
        EventHeader header = jsonMapper.readValue(message, EventHeader.class);
        String type = header.eventType() == null ? "" : header.eventType();

        switch (type) {
            case EventTypes.TRIP_SCHEDULED, EventTypes.TRIP_UPDATED -> {
                TripUpsertEvent event = jsonMapper.readValue(message, TripUpsertEvent.class);
                tripNotificationService.handleTripUpsert(event.eventId(), type, event.occurredAt(), event.payload());
            }
            case EventTypes.TRIP_DELETED -> {
                TripDeletedEvent event = jsonMapper.readValue(message, TripDeletedEvent.class);
                tripNotificationService.handleTripDeleted(event.eventId(), event.occurredAt(), event.payload());
            }
            case EventTypes.PRIVATE_TRIP_REJECTED -> {
                PrivateTripRejectedEvent event = jsonMapper.readValue(message, PrivateTripRejectedEvent.class);
                tripNotificationService.handlePrivateTripRejected(event.eventId(), event.occurredAt(),
                        event.payload());
            }
            default -> log.debug("Evento {} ignorado", type);
        }
    }
}