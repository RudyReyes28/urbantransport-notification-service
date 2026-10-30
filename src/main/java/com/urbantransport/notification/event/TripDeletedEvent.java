package com.urbantransport.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TripDeletedEvent(UUID eventId, Instant occurredAt, Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(UUID tripId, String type, UUID rentalId, UUID driverId) {
    }
}