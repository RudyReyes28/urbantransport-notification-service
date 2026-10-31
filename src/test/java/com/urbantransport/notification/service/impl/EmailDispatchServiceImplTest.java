package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationDispatchProperties;
import com.urbantransport.notification.domain.EmailLogEntity;
import com.urbantransport.notification.domain.EmailStatus;
import com.urbantransport.notification.exception.EmailSendException;
import com.urbantransport.notification.repository.EmailLogRepository;
import com.urbantransport.notification.service.EmailMessage;
import com.urbantransport.notification.service.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailDispatchServiceImplTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID EMAIL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final NotificationDispatchProperties PROPERTIES = new NotificationDispatchProperties(5,
            Duration.ofSeconds(30), Duration.ofMinutes(10), LEASE, 20);

    @Mock
    private EmailLogRepository emailLogRepository;

    @Mock
    private EmailSender emailSender;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Captor
    private ArgumentCaptor<EmailMessage> messageCaptor;

    private EmailDispatchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = service(PROPERTIES);
    }

    @Nested
    class Dispatch {

        @Test
        void claimedEmail_isSentAndMarkedSent() {
            EmailLogEntity email = email(0, "ana@mail.com", "<p>Hola</p>");
            claimReturns(email);

            service.dispatch(EMAIL_ID);

            verify(emailLogRepository).claim(EMAIL_ID, NOW, NOW.plus(LEASE));
            verify(emailSender).send(messageCaptor.capture());
            assertEquals("ana@mail.com", messageCaptor.getValue().to());
            assertEquals("Asunto", messageCaptor.getValue().subject());
            assertEquals("<p>Hola</p>", messageCaptor.getValue().htmlBody());
            assertEquals(EmailStatus.SENT, email.getStatus());
            assertEquals(1, email.getAttempts());
            assertEquals(NOW, email.getSentAt());
            assertNull(email.getLastError());
            assertNull(email.getNextAttemptAt());
        }

        @Test
        void emailClaimedByAnotherInstance_isNotSent() {
            when(emailLogRepository.claim(EMAIL_ID, NOW, NOW.plus(LEASE))).thenReturn(0);

            service.dispatch(EMAIL_ID);

            verify(emailLogRepository, never()).findById(any());
            verifyNoInteractions(emailSender);
        }

        @Test
        void claimedButMissingEmail_isNotSent() {
            when(emailLogRepository.claim(EMAIL_ID, NOW, NOW.plus(LEASE))).thenReturn(1);
            when(emailLogRepository.findById(EMAIL_ID)).thenReturn(Optional.empty());

            service.dispatch(EMAIL_ID);

            verify(emailLogRepository, times(1)).findById(EMAIL_ID);
            verifyNoInteractions(emailSender);
        }

        @ParameterizedTest
        @CsvSource(value = {"NULL, <p>Hola</p>", "ana@mail.com, NULL"}, nullValues = "NULL")
        void emailWithoutRecipientOrBody_failsPermanentlyWithoutSending(String recipient, String body) {
            EmailLogEntity email = email(0, recipient, body);
            claimReturns(email);

            service.dispatch(EMAIL_ID);

            verifyNoInteractions(emailSender);
            assertEquals(EmailStatus.FAILED, email.getStatus());
            assertEquals(1, email.getAttempts());
            assertEquals("El correo no tiene destinatario o cuerpo", email.getLastError());
            assertNull(email.getNextAttemptAt());
        }

        @Test
        void permanentFailure_marksFailedOnFirstAttempt() {
            EmailLogEntity email = email(0, "ana@mail.com", "<p>Hola</p>");
            claimReturns(email);
            doThrow(new EmailSendException("Destinatario invalido", true, null)).when(emailSender).send(any());

            service.dispatch(EMAIL_ID);

            assertEquals(EmailStatus.FAILED, email.getStatus());
            assertEquals(1, email.getAttempts());
            assertEquals("Destinatario invalido", email.getLastError());
            assertNull(email.getNextAttemptAt());
            assertNull(email.getSentAt());
        }

        @ParameterizedTest
        @CsvSource({"0, 30", "1, 60", "2, 120", "3, 240"})
        void temporaryFailure_isRetriedWithExponentialBackoff(int previousAttempts, long waitSeconds) {
            EmailLogEntity email = email(previousAttempts, "ana@mail.com", "<p>Hola</p>");
            claimReturns(email);
            doThrow(new EmailSendException("Gmail caido", false, null)).when(emailSender).send(any());

            service.dispatch(EMAIL_ID);

            assertEquals(EmailStatus.PENDING, email.getStatus());
            assertEquals(previousAttempts + 1, email.getAttempts());
            assertEquals("Gmail caido", email.getLastError());
            assertEquals(NOW.plusSeconds(waitSeconds), email.getNextAttemptAt());
        }

        @ParameterizedTest
        @CsvSource({"5, 600", "10, 600", "30, 600"})
        void longBackoff_isCappedAtMaxBackoff(int previousAttempts, long waitSeconds) {
            service = service(new NotificationDispatchProperties(100, Duration.ofSeconds(30), Duration.ofMinutes(10),
                    LEASE, 20));
            EmailLogEntity email = email(previousAttempts, "ana@mail.com", "<p>Hola</p>");
            claimReturns(email);
            doThrow(new EmailSendException("Gmail caido", false, null)).when(emailSender).send(any());

            service.dispatch(EMAIL_ID);

            assertEquals(EmailStatus.PENDING, email.getStatus());
            assertEquals(NOW.plusSeconds(waitSeconds), email.getNextAttemptAt());
        }

        @Test
        void temporaryFailureOnLastAttempt_marksFailed() {
            EmailLogEntity email = email(4, "ana@mail.com", "<p>Hola</p>");
            claimReturns(email);
            doThrow(new EmailSendException("Gmail caido", false, null)).when(emailSender).send(any());

            service.dispatch(EMAIL_ID);

            assertEquals(EmailStatus.FAILED, email.getStatus());
            assertEquals(5, email.getAttempts());
            assertNull(email.getNextAttemptAt());
        }

        @Test
        void unexpectedError_isTemporaryWithExceptionName() {
            EmailLogEntity email = email(0, "ana@mail.com", "<p>Hola</p>");
            claimReturns(email);
            doThrow(new IllegalStateException("boom")).when(emailSender).send(any());

            service.dispatch(EMAIL_ID);

            assertEquals(EmailStatus.PENDING, email.getStatus());
            assertEquals("IllegalStateException: boom", email.getLastError());
            assertEquals(NOW.plusSeconds(30), email.getNextAttemptAt());
        }

        @Test
        void longError_isTruncatedTo1000() {
            EmailLogEntity email = email(0, "ana@mail.com", "<p>Hola</p>");
            claimReturns(email);
            doThrow(new EmailSendException("e".repeat(1500), true, null)).when(emailSender).send(any());

            service.dispatch(EMAIL_ID);

            assertEquals("e".repeat(1000), email.getLastError());
        }

        @Test
        void emailDeletedWhileSending_isIgnoredWhenSavingOutcome() {
            EmailLogEntity email = email(0, "ana@mail.com", "<p>Hola</p>");
            when(emailLogRepository.claim(EMAIL_ID, NOW, NOW.plus(LEASE))).thenReturn(1);
            when(emailLogRepository.findById(EMAIL_ID)).thenReturn(Optional.of(email), Optional.empty());

            service.dispatch(EMAIL_ID);

            verify(emailSender).send(any());
            assertEquals(EmailStatus.PENDING, email.getStatus());
            assertEquals(0, email.getAttempts());
        }
    }

    private EmailDispatchServiceImpl service(NotificationDispatchProperties properties) {
        return new EmailDispatchServiceImpl(emailLogRepository, emailSender, properties, CLOCK, transactionManager);
    }

    private void claimReturns(EmailLogEntity email) {
        when(emailLogRepository.claim(EMAIL_ID, NOW, NOW.plus(LEASE))).thenReturn(1);
        when(emailLogRepository.findById(EMAIL_ID)).thenReturn(Optional.of(email));
    }

    private static EmailLogEntity email(int attempts, String recipient, String body) {
        return EmailLogEntity.builder()
                .id(EMAIL_ID)
                .eventId(UUID.randomUUID())
                .eventType("TicketPurchased")
                .recipient(recipient)
                .template("ticket-purchased")
                .subject("Asunto")
                .body(body)
                .status(EmailStatus.PENDING)
                .attempts(attempts)
                .nextAttemptAt(NOW.plus(LEASE))
                .build();
    }
}
