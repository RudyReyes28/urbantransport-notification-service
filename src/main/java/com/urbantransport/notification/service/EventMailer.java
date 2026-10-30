package com.urbantransport.notification.service;

public interface EventMailer {

    /** Encola un correo por destinatario (sin repetidos) o un SKIPPED si no hay ninguno. Requiere transacción. */
    void send(MailRequest request);
}