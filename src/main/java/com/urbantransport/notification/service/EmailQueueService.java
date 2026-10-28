package com.urbantransport.notification.service;

import java.util.UUID;

public interface EmailQueueService {

    /**
     * Deja el correo en la cola (PENDING). Debe llamarse dentro de una transacción: así el correo y el
     * registro del evento que lo originó se guardan juntos.
     *
     * @return false si ya existía un correo igual para ese evento, plantilla y destinatario
     */
    boolean enqueue(EmailRequest request);

    /** Registra que no se envió correo (por ejemplo, la compra no trae email). Mismo requisito de transacción. */
    boolean skip(UUID eventId, String eventType, UUID referenceId, String template, String subject, String reason);
}