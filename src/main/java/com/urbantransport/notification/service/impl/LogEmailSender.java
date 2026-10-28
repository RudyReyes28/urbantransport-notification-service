package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.exception.EmailSendException;
import com.urbantransport.notification.service.EmailMessage;
import com.urbantransport.notification.service.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Modo desarrollo (por defecto): no envía nada, solo deja el correo en el log. */
@Slf4j
@Service
@ConditionalOnProperty(name = "notification.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

    @Override
    public void send(EmailMessage message) {
        if (!message.hasValidRecipient()) {
            throw new EmailSendException("Destinatario inválido: " + message.to(), true, null);
        }
        log.info("[correo simulado] para={} asunto={}", message.to(), message.subject());
        log.debug("[correo simulado] cuerpo:\n{}", message.htmlBody());
    }
}