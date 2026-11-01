package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationDispatchProperties;
import com.urbantransport.notification.domain.EmailLogEntity;
import com.urbantransport.notification.domain.EmailStatus;
import com.urbantransport.notification.exception.EmailSendException;
import com.urbantransport.notification.repository.EmailLogRepository;
import com.urbantransport.notification.service.EmailDispatchService;
import com.urbantransport.notification.service.EmailMessage;
import com.urbantransport.notification.service.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Tres tiempos: reclamar (transacción corta), enviar (sin transacción, para no tener una conexión de base
 * abierta durante la llamada a Gmail) y registrar el resultado (otra transacción corta).
 */
@Slf4j
@Service
public class EmailDispatchServiceImpl implements EmailDispatchService {

    private static final int ERROR_MAX = 1000;

    private final EmailLogRepository emailLogRepository;
    private final EmailSender emailSender;
    private final NotificationDispatchProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public EmailDispatchServiceImpl(EmailLogRepository emailLogRepository, EmailSender emailSender,
                                    NotificationDispatchProperties properties, Clock clock,
                                    PlatformTransactionManager transactionManager) {
        this.emailLogRepository = emailLogRepository;
        this.emailSender = emailSender;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void dispatch(UUID emailId) {
        EmailLogEntity email = transaction.execute(status -> claim(emailId));
        if (email == null) {
            return;
        }
        Outcome outcome = send(email);
        transaction.executeWithoutResult(status -> saveOutcome(emailId, outcome));
    }

    private EmailLogEntity claim(UUID emailId) {
        Instant now = Instant.now(clock);
        if (emailLogRepository.claim(emailId, now, now.plus(properties.lease())) == 0) {
            return null;
        }
        return emailLogRepository.findById(emailId).orElse(null);
    }

    private Outcome send(EmailLogEntity email) {
        if (email.getRecipient() == null || email.getBody() == null) {
            return Outcome.failure("El correo no tiene destinatario o cuerpo", true);
        }
        try {
            emailSender.send(new EmailMessage(email.getRecipient(), email.getSubject(), email.getBody()));
            return Outcome.success();
        } catch (EmailSendException exception) {
            return Outcome.failure(exception.getMessage(), exception.isPermanent());
        } catch (RuntimeException exception) {
            log.error("Error inesperado al enviar el correo {}", email.getId(), exception);
            return Outcome.failure(exception.getClass().getSimpleName() + ": " + exception.getMessage(), false);
        }
    }

    private void saveOutcome(UUID emailId, Outcome outcome) {
        EmailLogEntity email = emailLogRepository.findById(emailId).orElse(null);
        if (email == null) {
            return;
        }
        Instant now = Instant.now(clock);
        int attempts = email.getAttempts() + 1;
        email.setAttempts(attempts);

        if (outcome.sent()) {
            email.setStatus(EmailStatus.SENT);
            email.setSentAt(now);
            email.setLastError(null);
            email.setNextAttemptAt(null);
            log.info("Correo {} enviado a {} (intento {})", emailId, email.getRecipient(), attempts);
        } else if (outcome.permanent() || attempts >= properties.maxAttempts()) {
            email.setStatus(EmailStatus.FAILED);
            email.setLastError(truncate(outcome.error()));
            email.setNextAttemptAt(null);
            log.warn("Correo {} falló definitivamente tras {} intento(s): {}", emailId, attempts, outcome.error());
        } else {
            Duration wait = backoff(attempts);
            email.setStatus(EmailStatus.PENDING);
            email.setLastError(truncate(outcome.error()));
            email.setNextAttemptAt(now.plus(wait));
            log.warn("Correo {} falló (intento {}), se reintenta en {}: {}", emailId, attempts, wait, outcome.error());
        }
    }

    /** initial, 2×, 4×… con tope. */
    private Duration backoff(int attempts) {
        Duration wait = properties.initialBackoff().multipliedBy(1L << Math.min(attempts - 1, 20));
        return wait.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : wait;
    }

    private String truncate(String value) {
        return value == null || value.length() <= ERROR_MAX ? value : value.substring(0, ERROR_MAX);
    }

    private record Outcome(boolean sent, boolean permanent, String error) {

        static Outcome success() {
            return new Outcome(true, false, null);
        }

        static Outcome failure(String error, boolean permanent) {
            return new Outcome(false, permanent, error);
        }
    }
}