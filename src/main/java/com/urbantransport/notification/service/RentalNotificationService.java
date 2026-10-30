package com.urbantransport.notification.service;

import com.urbantransport.notification.event.RentalEvent;

import java.time.Instant;
import java.util.UUID;

public interface RentalNotificationService {

    /** Solicitud en línea: aviso a los administradores y acuse al cliente. */
    void handleRequested(UUID eventId, Instant occurredAt, RentalEvent.Payload payload);

    /** Cotización lista: precio al cliente. */
    void handleQuoted(UUID eventId, Instant occurredAt, RentalEvent.Payload payload);

    /** Solicitud rechazada: motivo al cliente. */
    void handleRejected(UUID eventId, Instant occurredAt, RentalEvent.Payload payload);

    /** Pago registrado: recibo al cliente y aviso a los administradores de que falta asignar bus y chofer. */
    void handlePaid(UUID eventId, Instant occurredAt, RentalEvent.Payload payload);
}