package com.urbantransport.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** DriverRegistered, DriverUpdated y DriverDeactivated comparten la misma forma de payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DriverEvent(UUID eventId, String eventType, Instant occurredAt, Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID driverId,
            UUID userId,
            String fullName,
            String licenseNumber,
            LocalDate licenseExpiresOn,
            String email,
            UUID branchId,
            boolean active) {
    }
}