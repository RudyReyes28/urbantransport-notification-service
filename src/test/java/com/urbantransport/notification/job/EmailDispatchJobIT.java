package com.urbantransport.notification.job;

import com.urbantransport.notification.AbstractIntegrationTest;
import com.urbantransport.notification.domain.EmailLogEntity;
import com.urbantransport.notification.domain.EmailStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailDispatchJobIT extends AbstractIntegrationTest {

    @Autowired
    private EmailDispatchJob emailDispatchJob;

    @Test
    void dueEmail_isSentThroughLogSender() {
        EmailLogEntity email = saveEmail(EmailStatus.PENDING, "ana@mail.com", "TicketPurchased", null,
                Instant.now().minusSeconds(5));

        emailDispatchJob.dispatchDue();

        EmailLogEntity saved = emailLogRepository.findById(email.getId()).orElseThrow();
        assertEquals(EmailStatus.SENT, saved.getStatus());
        assertEquals(1, saved.getAttempts());
        assertNotNull(saved.getSentAt());
        assertNull(saved.getNextAttemptAt());
        assertNull(saved.getLastError());
    }

    @Test
    void invalidRecipient_failsPermanentlyOnFirstAttempt() {
        EmailLogEntity email = saveEmail(EmailStatus.PENDING, "no-es-correo", "TicketPurchased", null,
                Instant.now().minusSeconds(5));

        emailDispatchJob.dispatchDue();

        EmailLogEntity saved = emailLogRepository.findById(email.getId()).orElseThrow();
        assertEquals(EmailStatus.FAILED, saved.getStatus());
        assertEquals(1, saved.getAttempts());
        assertEquals("Destinatario inválido: no-es-correo", saved.getLastError());
        assertNull(saved.getNextAttemptAt());
        assertNull(saved.getSentAt());
    }

    @Test
    void pendingWithoutRecipient_failsWithoutSending() {
        EmailLogEntity email = saveEmail(EmailStatus.PENDING, null, "TicketPurchased", null,
                Instant.now().minusSeconds(5));

        emailDispatchJob.dispatchDue();

        EmailLogEntity saved = emailLogRepository.findById(email.getId()).orElseThrow();
        assertEquals(EmailStatus.FAILED, saved.getStatus());
        assertEquals("El correo no tiene destinatario o cuerpo", saved.getLastError());
    }

    @Test
    void notDueAndNonPendingEmails_areLeftAlone() {
        Instant later = Instant.now().plusSeconds(600);
        EmailLogEntity future = saveEmail(EmailStatus.PENDING, "ana@mail.com", "TicketPurchased", null, later);
        EmailLogEntity skipped = saveEmail(EmailStatus.SKIPPED, null, "TicketPurchased", null, null);
        EmailLogEntity failed = saveEmail(EmailStatus.FAILED, "luis@mail.com", "TicketPurchased", null,
                Instant.now().minusSeconds(5));

        emailDispatchJob.dispatchDue();

        EmailLogEntity savedFuture = emailLogRepository.findById(future.getId()).orElseThrow();
        assertEquals(EmailStatus.PENDING, savedFuture.getStatus());
        assertEquals(0, savedFuture.getAttempts());
        assertEquals(EmailStatus.SKIPPED, emailLogRepository.findById(skipped.getId()).orElseThrow().getStatus());
        EmailLogEntity savedFailed = emailLogRepository.findById(failed.getId()).orElseThrow();
        assertEquals(EmailStatus.FAILED, savedFailed.getStatus());
        assertEquals(5, savedFailed.getAttempts());
    }

    @Test
    void failedEmailRetriedByAdmin_isSentOnNextRun() throws Exception {
        EmailLogEntity failed = saveEmail(EmailStatus.FAILED, "ana@mail.com", "TicketPurchased", null, null);

        mockMvc.perform(post("/emails/{id}/retry", failed.getId()).with(systemAdmin()))
                .andExpect(status().isOk());
        emailDispatchJob.dispatchDue();

        EmailLogEntity saved = emailLogRepository.findById(failed.getId()).orElseThrow();
        assertEquals(EmailStatus.SENT, saved.getStatus());
        assertEquals(1, saved.getAttempts());
        assertNull(saved.getLastError());
    }
}
