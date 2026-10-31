package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.domain.EmailLogEntity;
import com.urbantransport.notification.domain.EmailStatus;
import com.urbantransport.notification.repository.EmailLogRepository;
import com.urbantransport.notification.service.EmailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailQueueServiceImplTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REFERENCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String TEMPLATE = "ticket-purchased";
    private static final String RECIPIENT = "ana@mail.com";

    @Mock
    private EmailLogRepository emailLogRepository;

    @Captor
    private ArgumentCaptor<EmailLogEntity> emailCaptor;

    private EmailQueueServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmailQueueServiceImpl(emailLogRepository, CLOCK);
    }

    @Nested
    class Enqueue {

        @Test
        void newEmail_isSavedPendingAndDueNow() {
            when(emailLogRepository.existsDuplicate(EVENT_ID, TEMPLATE, RECIPIENT)).thenReturn(false);

            boolean enqueued = service.enqueue(request(RECIPIENT, "Tu compra"));

            assertTrue(enqueued);
            verify(emailLogRepository).save(emailCaptor.capture());
            EmailLogEntity saved = emailCaptor.getValue();
            assertNotNull(saved.getId());
            assertEquals(EVENT_ID, saved.getEventId());
            assertEquals("TicketPurchased", saved.getEventType());
            assertEquals(REFERENCE_ID, saved.getReferenceId());
            assertEquals(RECIPIENT, saved.getRecipient());
            assertEquals(TEMPLATE, saved.getTemplate());
            assertEquals("Tu compra", saved.getSubject());
            assertEquals("<p>Hola</p>", saved.getBody());
            assertEquals(EmailStatus.PENDING, saved.getStatus());
            assertEquals(0, saved.getAttempts());
            assertEquals(NOW, saved.getNextAttemptAt());
            assertNull(saved.getLastError());
            assertNull(saved.getSentAt());
        }

        @Test
        void longSubject_isTruncatedTo200() {
            when(emailLogRepository.existsDuplicate(EVENT_ID, TEMPLATE, RECIPIENT)).thenReturn(false);

            service.enqueue(request(RECIPIENT, "x".repeat(250)));

            verify(emailLogRepository).save(emailCaptor.capture());
            assertEquals("x".repeat(200), emailCaptor.getValue().getSubject());
        }

        @Test
        void duplicatedEmail_isNotSaved() {
            when(emailLogRepository.existsDuplicate(EVENT_ID, TEMPLATE, RECIPIENT)).thenReturn(true);

            boolean enqueued = service.enqueue(request(RECIPIENT, "Tu compra"));

            assertFalse(enqueued);
            verify(emailLogRepository, never()).save(any());
        }

        @Test
        void withoutRecipient_checksDuplicateWithEmptyRecipient() {
            when(emailLogRepository.existsDuplicate(EVENT_ID, TEMPLATE, "")).thenReturn(true);

            assertFalse(service.enqueue(request(null, "Tu compra")));

            verify(emailLogRepository, never()).save(any());
        }
    }

    @Nested
    class Skip {

        @Test
        void newSkip_isSavedWithReasonAndWithoutRecipient() {
            when(emailLogRepository.existsDuplicate(EVENT_ID, TEMPLATE, "")).thenReturn(false);

            boolean skipped = service.skip(EVENT_ID, "TicketPurchased", REFERENCE_ID, TEMPLATE, "Tu compra",
                    "Venta en ventanilla");

            assertTrue(skipped);
            verify(emailLogRepository).save(emailCaptor.capture());
            EmailLogEntity saved = emailCaptor.getValue();
            assertNotNull(saved.getId());
            assertEquals(EVENT_ID, saved.getEventId());
            assertEquals(REFERENCE_ID, saved.getReferenceId());
            assertNull(saved.getRecipient());
            assertNull(saved.getBody());
            assertEquals(EmailStatus.SKIPPED, saved.getStatus());
            assertEquals(0, saved.getAttempts());
            assertEquals("Venta en ventanilla", saved.getLastError());
            assertNull(saved.getNextAttemptAt());
        }

        @Test
        void longSubjectAndReason_areTruncated() {
            when(emailLogRepository.existsDuplicate(EVENT_ID, TEMPLATE, "")).thenReturn(false);

            service.skip(EVENT_ID, "TicketPurchased", REFERENCE_ID, TEMPLATE, "s".repeat(201), "r".repeat(1500));

            verify(emailLogRepository).save(emailCaptor.capture());
            assertEquals("s".repeat(200), emailCaptor.getValue().getSubject());
            assertEquals("r".repeat(1000), emailCaptor.getValue().getLastError());
        }

        @Test
        void duplicatedSkip_isNotSaved() {
            when(emailLogRepository.existsDuplicate(EVENT_ID, TEMPLATE, "")).thenReturn(true);

            boolean skipped = service.skip(EVENT_ID, "TicketPurchased", REFERENCE_ID, TEMPLATE, "Tu compra", "x");

            assertFalse(skipped);
            verify(emailLogRepository, never()).save(any());
        }
    }

    private static EmailRequest request(String recipient, String subject) {
        return new EmailRequest(EVENT_ID, "TicketPurchased", REFERENCE_ID, recipient, TEMPLATE, subject,
                "<p>Hola</p>");
    }
}
