package com.urbantransport.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketPurchasedEvent(UUID eventId, Instant occurredAt, Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            UUID orderId,
            String channel,
            UUID branchId,
            String customerNit,
            String buyerEmail,
            LocalDate paymentDate,
            BigDecimal total,
            Trip trip,
            List<Ticket> tickets) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Trip(UUID tripId, String originName, String destinationName, LocalDateTime departureAt,
                       String busPlate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ticket(UUID ticketId, int seatNumber, String seatLabel, BigDecimal pricePaid) {
    }
}