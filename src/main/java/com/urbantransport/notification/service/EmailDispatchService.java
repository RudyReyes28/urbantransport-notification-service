package com.urbantransport.notification.service;

import java.util.UUID;

public interface EmailDispatchService {

    /** Intenta enviar un correo pendiente y registra el resultado. No hace nada si otro ya lo reclamó. */
    void dispatch(UUID emailId);
}