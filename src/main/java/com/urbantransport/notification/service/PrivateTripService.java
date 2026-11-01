package com.urbantransport.notification.service;

import com.urbantransport.notification.domain.TripDirectoryEntity;
import com.urbantransport.notification.event.RentalAssignedEvent;

import java.time.Instant;
import java.util.UUID;

public interface PrivateTripService {

    /** Guarda los tramos de una asignación y, si trip ya programó los viajes, avisa. Idempotente por eventId. */
    void recordAssignment(UUID eventId, Instant occurredAt, RentalAssignedEvent.Payload payload);

    /**
     * Un viaje privado ya programado por trip: lo une con su tramo y, cuando todos los tramos del alquiler están
     * programados, avisa al cliente y al chofer. Se llama dentro de la transacción del evento de trip.
     */
    void linkScheduledTrip(Instant occurredAt, TripDirectoryEntity trip);
}