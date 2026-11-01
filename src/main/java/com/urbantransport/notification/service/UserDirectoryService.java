package com.urbantransport.notification.service;

import com.urbantransport.notification.event.UserEvent;

public interface UserDirectoryService {

    /** Aplica un evento de usuario de identity al directorio (idempotente por eventId). */
    void applyUserEvent(UserEvent event);
}