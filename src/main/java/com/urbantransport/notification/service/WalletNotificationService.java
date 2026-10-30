package com.urbantransport.notification.service;

import com.urbantransport.notification.event.WalletRechargedEvent;

import java.time.Instant;
import java.util.UUID;

public interface WalletNotificationService {

    /** Comprobante de recarga al cliente. Idempotente por eventId. */
    void handleRecharged(UUID eventId, Instant occurredAt, WalletRechargedEvent.Payload payload);
}