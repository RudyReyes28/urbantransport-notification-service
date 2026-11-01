package com.urbantransport.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WalletRechargedEvent(UUID eventId, Instant occurredAt, Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(UUID customerId, BigDecimal amount, LocalDate paymentDate) {
    }
}