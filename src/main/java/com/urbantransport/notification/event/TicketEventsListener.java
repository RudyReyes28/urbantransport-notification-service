package com.urbantransport.notification.event;

import com.urbantransport.notification.service.TicketNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketEventsListener {

    private final JsonMapper jsonMapper;
    private final TicketNotificationService ticketNotificationService;

    @KafkaListener(topics = "${notification.kafka.ticket-events-topic}")
    public void onEvent(String message) {
        EventHeader header = jsonMapper.readValue(message, EventHeader.class);
        String type = header.eventType() == null ? "" : header.eventType();

        switch (type) {
            case EventTypes.TICKET_PURCHASED -> {
                TicketPurchasedEvent event = jsonMapper.readValue(message, TicketPurchasedEvent.class);
                ticketNotificationService.handleTicketPurchased(event.eventId(), event.occurredAt(), event.payload());
            }
            case EventTypes.TICKET_CANCELLED -> {
                TicketCancelledEvent event = jsonMapper.readValue(message, TicketCancelledEvent.class);
                ticketNotificationService.handleTicketCancelled(event.eventId(), event.occurredAt(), event.payload());
            }
            default -> log.debug("Evento {} ignorado", type);
        }
    }
}