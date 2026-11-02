package com.urbantransport.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PasswordSetupRequestedEvent(UUID eventId, String eventType, Instant occurredAt, Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID userId,
            String email,
            String fullName,
            String role,
            String purpose,
            String setupUrl,
            Instant expiresAt) {
    }
}
