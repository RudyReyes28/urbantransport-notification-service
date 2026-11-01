package com.urbantransport.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketCancelledEvent(UUID eventId, Instant occurredAt, Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID ticketId,
            UUID orderId,
            UUID tripId,
            int seatNumber,
            String seatLabel,
            String channel,
            UUID branchId,
            String customerNit,
            String buyerEmail,
            BigDecimal pricePaid,
            String reason,
            String refundMethod,
            String originName,
            String destinationName,
            LocalDateTime departureAt) {
    }
}