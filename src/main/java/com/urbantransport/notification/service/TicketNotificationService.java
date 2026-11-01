package com.urbantransport.notification.service;

import com.urbantransport.notification.event.TicketCancelledEvent;
import com.urbantransport.notification.event.TicketPurchasedEvent;

import java.time.Instant;
import java.util.UUID;

public interface TicketNotificationService {

    /** Confirmación de compra al comprador. Idempotente por eventId. */
    void handleTicketPurchased(UUID eventId, Instant occurredAt, TicketPurchasedEvent.Payload payload);

    /** Cancelación: al comprador (reembolso a cartera) o a los administradores de la sucursal (reembolso en efectivo). */
    void handleTicketCancelled(UUID eventId, Instant occurredAt, TicketCancelledEvent.Payload payload);
}