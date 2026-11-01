package com.urbantransport.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Las fechas de los tramos son instantes (UTC); se convierten a hora de Guatemala al guardarlas. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RentalAssignedEvent(UUID eventId, Instant occurredAt, Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID rentalId,
            UUID branchId,
            UUID busId,
            UUID driverId,
            Integer passengers,
            String customerNit,
            UUID customerUserId,
            List<Leg> legs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Leg(String originText, String destinationText, Instant departureAt, Instant estimatedArrival) {
    }
}