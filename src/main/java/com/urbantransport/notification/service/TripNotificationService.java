package com.urbantransport.notification.service;

import com.urbantransport.notification.event.PrivateTripRejectedEvent;
import com.urbantransport.notification.event.TripDeletedEvent;
import com.urbantransport.notification.event.TripUpsertEvent;

import java.time.Instant;
import java.util.UUID;

public interface TripNotificationService {

    /**
     * TripScheduled o TripUpdated. Siempre actualiza el estado guardado del viaje. Regular programado: avisa al
     * chofer. Regular modificado: avisa a pasajeros y chofer lo que cambió. Privado: lo une con su asignación.
     */
    void handleTripUpsert(UUID eventId, String eventType, Instant occurredAt, TripUpsertEvent.Payload payload);

    /** Viaje eliminado: avisa al chofer y, si era privado, al cliente. Los pasajeros ya reciben su cancelación. */
    void handleTripDeleted(UUID eventId, Instant occurredAt, TripDeletedEvent.Payload payload);

    /** Trip no pudo programar un viaje privado: avisa a los administradores de la sucursal con el motivo. */
    void handlePrivateTripRejected(UUID eventId, Instant occurredAt, PrivateTripRejectedEvent.Payload payload);
}