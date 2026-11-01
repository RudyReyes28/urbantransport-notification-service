package com.urbantransport.notification.exception;

import lombok.Getter;

/**
 * Falla al enviar un correo. permanent=true: reintentar no sirve (dirección inválida);
 * permanent=false: puede ser temporal (red, Gmail caído, credenciales).
 */
@Getter
public class EmailSendException extends RuntimeException {

    private final boolean permanent;

    public EmailSendException(String message, boolean permanent, Throwable cause) {
        super(message, cause);
        this.permanent = permanent;
    }
}