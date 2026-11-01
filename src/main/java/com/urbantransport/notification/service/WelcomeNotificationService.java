package com.urbantransport.notification.service;

import com.urbantransport.notification.event.UserEvent;

public interface WelcomeNotificationService {

    /**
     * Bienvenida al crearse una cuenta (cliente o personal). Sin contraseñas. No usa processed_events: el índice
     * único de email_log evita enviarla dos veces aunque el evento se reentregue.
     */
    void handleUserCreated(UserEvent event);
}