package com.urbantransport.notification.service;

import com.urbantransport.notification.event.DriverEvent;

public interface DriverDirectoryService {

    /** Aplica un evento de chofer de fleet al directorio (idempotente por eventId). */
    void applyDriverEvent(DriverEvent event);
}