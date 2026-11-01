package com.urbantransport.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** TripScheduled y TripUpdated comparten payload. Los viajes regulares traen originName/destinationName; los privados, originText/destinationText. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TripUpsertEvent(UUID eventId, Instant occurredAt, Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID tripId,
            String type,
            String originName,
            String destinationName,
            String originText,
            String destinationText,
            UUID busId,
            String busPlate,
            UUID driverId,
            LocalDateTime departureAt,
            LocalDateTime estimatedArrival,
            String status) {
    }
}