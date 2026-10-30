package com.urbantransport.notification.service;

import com.urbantransport.notification.event.TicketPurchasedEvent;

import java.util.UUID;

public interface TripPassengerService {

    /** Guarda quién compró cada boleto (solo compras con correo). Se llama dentro de la transacción del evento. */
    void record(TicketPurchasedEvent.Payload payload);

    /** Quita un boleto cancelado. */
    void remove(UUID ticketId);
}