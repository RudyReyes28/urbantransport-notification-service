package com.urbantransport.notification.event;

import com.urbantransport.notification.service.WalletNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventsListener {

    private final JsonMapper jsonMapper;
    private final WalletNotificationService walletNotificationService;

    @KafkaListener(topics = "${notification.kafka.payment-events-topic}")
    public void onEvent(String message) {
        EventHeader header = jsonMapper.readValue(message, EventHeader.class);
        String type = header.eventType() == null ? "" : header.eventType();

        switch (type) {
            case EventTypes.WALLET_RECHARGED -> {
                WalletRechargedEvent event = jsonMapper.readValue(message, WalletRechargedEvent.class);
                walletNotificationService.handleRecharged(event.eventId(), event.occurredAt(), event.payload());
            }
            default -> log.debug("Evento {} ignorado", type);
        }
    }
}