package com.urbantransport.notification.service;

import java.util.Objects;
import java.util.regex.Pattern;

/** Un correo listo para enviar. Destinatario y asunto se limpian de saltos de línea (evita inyección de cabeceras). */
public record EmailMessage(String to, String subject, String htmlBody) {

    private static final Pattern ADDRESS = Pattern.compile("^[^\\s,;<>@]+@[^\\s,;<>@]+\\.[^\\s,;<>@]+$");

    public EmailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(htmlBody, "htmlBody");
        to = singleLine(to);
        subject = singleLine(subject);
    }

    /** Una sola dirección con forma válida. */
    public boolean hasValidRecipient() {
        return ADDRESS.matcher(to).matches();
    }

    private static String singleLine(String value) {
        return value.replaceAll("[\\r\\n]+", " ").trim();
    }
}