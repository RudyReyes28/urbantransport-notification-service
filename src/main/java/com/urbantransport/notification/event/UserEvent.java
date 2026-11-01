package com.urbantransport.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/** Evento de usuario de identity. El rol llega como texto para no fallar si identity agrega uno nuevo. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserEvent(UUID eventId, String eventType, Instant occurredAt, UserPayload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserPayload(
            UUID userId,
            String email,
            String fullName,
            String role,
            UUID branchId,
            UUID customerId,
            boolean active) {
    }
}