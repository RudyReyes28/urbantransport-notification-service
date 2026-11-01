package com.urbantransport.notification.service;

import com.urbantransport.notification.exception.EmailSendException;

public interface EmailSender {

    /** Envía el correo. @throws EmailSendException si no se pudo  */
    void send(EmailMessage message);
}