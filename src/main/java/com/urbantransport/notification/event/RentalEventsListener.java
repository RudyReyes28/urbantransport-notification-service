package com.urbantransport.notification.event;

import com.urbantransport.notification.service.PrivateTripService;
import com.urbantransport.notification.service.RentalNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RentalEventsListener {

    private final JsonMapper jsonMapper;
    private final RentalNotificationService rentalNotificationService;
    private final PrivateTripService privateTripService;

    @KafkaListener(topics = "${notification.kafka.rental-events-topic}")
    public void onEvent(String message) {
        EventHeader header = jsonMapper.readValue(message, EventHeader.class);
        String type = header.eventType() == null ? "" : header.eventType();

        switch (type) {
            case EventTypes.RENTAL_REQUESTED -> {
                RentalEvent event = jsonMapper.readValue(message, RentalEvent.class);
                rentalNotificationService.handleRequested(event.eventId(), event.occurredAt(), event.payload());
            }
            case EventTypes.RENTAL_QUOTED -> {
                RentalEvent event = jsonMapper.readValue(message, RentalEvent.class);
                rentalNotificationService.handleQuoted(event.eventId(), event.occurredAt(), event.payload());
            }
            case EventTypes.RENTAL_REJECTED -> {
                RentalEvent event = jsonMapper.readValue(message, RentalEvent.class);
                rentalNotificationService.handleRejected(event.eventId(), event.occurredAt(), event.payload());
            }
            case EventTypes.RENTAL_PAID -> {
                RentalEvent event = jsonMapper.readValue(message, RentalEvent.class);
                rentalNotificationService.handlePaid(event.eventId(), event.occurredAt(), event.payload());
            }
            case EventTypes.RENTAL_ASSIGNED -> {
                RentalAssignedEvent event = jsonMapper.readValue(message, RentalAssignedEvent.class);
                privateTripService.recordAssignment(event.eventId(), event.occurredAt(), event.payload());
            }
            default -> log.debug("Evento {} ignorado", type);
        }
    }
}