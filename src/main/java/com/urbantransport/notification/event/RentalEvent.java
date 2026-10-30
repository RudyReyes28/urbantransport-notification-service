package com.urbantransport.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** RentalRequested, RentalQuoted, RentalRejected y RentalPaid comparten la misma forma de payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RentalEvent(UUID eventId, Instant occurredAt, Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID rentalId,
            String channel,
            UUID branchId,
            String customerNit,
            UUID customerUserId,
            String originText,
            String destinationText,
            LocalDate departureDate,
            LocalDate returnDate,
            Integer passengers,
            BigDecimal distanceKm,
            BigDecimal estimatedPrice,
            BigDecimal confirmedPrice,
            LocalDate paymentDate,
            String rejectionReason,
            String status) {
    }
}