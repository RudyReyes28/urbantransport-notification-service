package com.urbantransport.notification.service;

import com.urbantransport.notification.dto.NoticeView;

import java.util.Collection;
import java.util.UUID;

/**
 * Un correo lógico para varios destinatarios. Si la lista queda vacía, se registra una fila SKIPPED con
 * skipReason y no se renderiza nada.
 */
public record MailRequest(
        UUID eventId,
        String eventType,
        UUID referenceId,
        String template,
        String subject,
        NoticeView view,
        Collection<String> recipients,
        String skipReason) {
}