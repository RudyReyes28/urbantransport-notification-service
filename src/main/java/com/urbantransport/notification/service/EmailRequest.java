package com.urbantransport.notification.service;

import java.util.UUID;

/** Lo que hace falta para encolar un correo ya armado (el HTML lo genera EmailRenderer). */
public record EmailRequest(
        UUID eventId,
        String eventType,
        UUID referenceId,
        String recipient,
        String template,
        String subject,
        String htmlBody) {
}