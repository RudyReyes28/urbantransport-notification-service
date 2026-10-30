package com.urbantransport.notification.event;

import com.urbantransport.notification.service.UserDirectoryService;
import com.urbantransport.notification.service.WelcomeNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityEventsListener {

    private final JsonMapper jsonMapper;
    private final UserDirectoryService userDirectoryService;
    private final WelcomeNotificationService welcomeNotificationService;

    @KafkaListener(topics = "${notification.kafka.identity-events-topic}")
    public void onEvent(String message) {
        EventHeader header = jsonMapper.readValue(message, EventHeader.class);
        String type = header.eventType() == null ? "" : header.eventType();

        switch (type) {
            case EventTypes.USER_CREATED -> {
                UserEvent event = jsonMapper.readValue(message, UserEvent.class);
                userDirectoryService.applyUserEvent(event);
                welcomeNotificationService.handleUserCreated(event);
            }
            case EventTypes.USER_UPDATED, EventTypes.USER_DEACTIVATED ->
                    userDirectoryService.applyUserEvent(jsonMapper.readValue(message, UserEvent.class));
            default -> log.debug("Evento {} ignorado", type);
        }
    }
}