package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationMailProperties;
import com.urbantransport.notification.exception.EmailSendException;
import com.urbantransport.notification.service.EmailMessage;
import com.urbantransport.notification.service.EmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

/** Envío real por SMTP (Gmail con contraseña de aplicación). Solo se activa con notification.mail.enabled=true. */
@Slf4j
@Service
@ConditionalOnProperty(name = "notification.mail.enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final NotificationMailProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, NotificationMailProperties properties,
                           @Value("${spring.mail.password:}") String password) {
        if (properties.fromAddress() == null || properties.fromAddress().isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                    "MAIL_ENABLED=true requiere MAIL_USERNAME y MAIL_PASSWORD (contraseña de aplicación de Gmail)");
        }
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(EmailMessage message) {
        if (!message.hasValidRecipient()) {
            throw new EmailSendException("Destinatario inválido: " + message.to(), true, null);
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, "UTF-8");
            helper.setFrom(properties.fromAddress(), properties.fromName());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.htmlBody(), true);
            mailSender.send(mime);
            log.debug("Correo enviado a {}", message.to());
        } catch (MailAuthenticationException exception) {
            throw new EmailSendException(
                    "Gmail rechazó las credenciales: revisa MAIL_USERNAME y MAIL_PASSWORD", false, exception);
        } catch (MailException exception) {
            throw new EmailSendException("No se pudo enviar el correo: " + exception.getMessage(),
                    isPermanent(exception), exception);
        } catch (MessagingException | UnsupportedEncodingException exception) {
            throw new EmailSendException("No se pudo armar el correo: " + exception.getMessage(), true, exception);
        }
    }

    /** Dirección rechazada o mal formada: reintentar no ayuda. Todo lo demás (red, Gmail) se considera temporal. */
    private static boolean isPermanent(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof AddressException || cause instanceof MailParseException) {
                return true;
            }
            if (cause instanceof SendFailedException failed
                    && failed.getInvalidAddresses() != null && failed.getInvalidAddresses().length > 0) {
                return true;
            }
            if (cause instanceof MailSendException sendException) {
                for (Exception inner : sendException.getMessageExceptions()) {
                    if (isPermanent(inner)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}