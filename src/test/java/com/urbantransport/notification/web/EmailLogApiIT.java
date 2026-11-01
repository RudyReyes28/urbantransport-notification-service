package com.urbantransport.notification.web;

import com.urbantransport.notification.AbstractIntegrationTest;
import com.urbantransport.notification.domain.EmailLogEntity;
import com.urbantransport.notification.domain.EmailStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailLogApiIT extends AbstractIntegrationTest {

    @Test
    void search_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/emails"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BRANCH_ADMIN", "CASHIER", "DRIVER", "CUSTOMER"})
    void search_withoutSystemAdminRole_returnsForbidden(String role) throws Exception {
        mockMvc.perform(get("/emails").with(userWithRole(role, UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void retry_withoutSystemAdminRole_isForbiddenAndChangesNothing() throws Exception {
        EmailLogEntity failed = saveEmail(EmailStatus.FAILED, "ana@mail.com", "TicketPurchased", null, null);

        mockMvc.perform(post("/emails/{id}/retry", failed.getId()).with(userWithRole("BRANCH_ADMIN", USER_ID)))
                .andExpect(status().isForbidden());

        assertEquals(EmailStatus.FAILED, emailLogRepository.findById(failed.getId()).orElseThrow().getStatus());
    }

    @Test
    void search_withoutFilters_returnsAllNewestFirst() throws Exception {
        EmailLogEntity oldest = saveEmail(EmailStatus.SENT, "a@mail.com", "TicketPurchased", null, null);
        EmailLogEntity middle = saveEmail(EmailStatus.FAILED, "b@mail.com", "TicketCancelled", null, null);
        EmailLogEntity newest = saveEmail(EmailStatus.SKIPPED, null, "RentalQuoted", null, null);
        setCreatedAt(oldest.getId(), Instant.now().minusSeconds(300));
        setCreatedAt(middle.getId(), Instant.now().minusSeconds(200));
        setCreatedAt(newest.getId(), Instant.now().minusSeconds(100));

        mockMvc.perform(get("/emails").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].id").value(newest.getId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(middle.getId().toString()))
                .andExpect(jsonPath("$.content[2].id").value(oldest.getId().toString()))
                .andExpect(jsonPath("$.content[2].status").value("SENT"))
                .andExpect(jsonPath("$.content[0].htmlBody").doesNotExist());
    }

    @Test
    void search_byStatus_returnsOnlyThatStatus() throws Exception {
        saveEmail(EmailStatus.SENT, "a@mail.com", "TicketPurchased", null, null);
        EmailLogEntity failed = saveEmail(EmailStatus.FAILED, "b@mail.com", "TicketPurchased", null, null);

        mockMvc.perform(get("/emails").param("status", "FAILED").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(failed.getId().toString()))
                .andExpect(jsonPath("$.content[0].lastError").value("Gmail caido"));
    }

    @Test
    void search_byRecipient_isPartialCaseInsensitiveAndEscapesWildcards() throws Exception {
        EmailLogEntity underscore = saveEmail(EmailStatus.SENT, "ana_lopez@mail.com", "TicketPurchased", null, null);
        saveEmail(EmailStatus.SENT, "anaxlopez@mail.com", "TicketPurchased", null, null);
        saveEmail(EmailStatus.SKIPPED, null, "TicketPurchased", null, null);

        mockMvc.perform(get("/emails").param("recipient", " ANA_LOPEZ ").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(underscore.getId().toString()));

        mockMvc.perform(get("/emails").param("recipient", "lopez@").with(systemAdmin()))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/emails").param("recipient", "%").with(systemAdmin()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void search_byEventTypeAndReference_combinesFilters() throws Exception {
        UUID referenceId = UUID.randomUUID();
        EmailLogEntity match = saveEmail(EmailStatus.PENDING, "a@mail.com", "TicketPurchased", referenceId, null);
        saveEmail(EmailStatus.PENDING, "b@mail.com", "TicketCancelled", referenceId, null);
        saveEmail(EmailStatus.PENDING, "c@mail.com", "TicketPurchased", UUID.randomUUID(), null);

        mockMvc.perform(get("/emails").param("eventType", " TicketPurchased ")
                        .param("referenceId", referenceId.toString()).with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(match.getId().toString()))
                .andExpect(jsonPath("$.content[0].referenceId").value(referenceId.toString()));
    }

    @Test
    void search_byDateRange_usesGuatemalaDaysInclusive() throws Exception {
        EmailLogEntity tenDaysAgo = saveEmail(EmailStatus.SENT, "a@mail.com", "TicketPurchased", null, null);
        EmailLogEntity fiveDaysAgo = saveEmail(EmailStatus.SENT, "b@mail.com", "TicketPurchased", null, null);
        EmailLogEntity twoDaysAgo = saveEmail(EmailStatus.SENT, "c@mail.com", "TicketPurchased", null, null);
        EmailLogEntity todays = saveEmail(EmailStatus.SENT, "d@mail.com", "TicketPurchased", null, null);
        setCreatedAt(tenDaysAgo.getId(), today().minusDays(10).atTime(12, 0).atZone(ZONE).toInstant());
        setCreatedAt(fiveDaysAgo.getId(), today().minusDays(5).atStartOfDay(ZONE).toInstant());
        setCreatedAt(twoDaysAgo.getId(), today().minusDays(2).atTime(23, 59).atZone(ZONE).toInstant());
        setCreatedAt(todays.getId(), today().atStartOfDay(ZONE).toInstant());

        mockMvc.perform(get("/emails").param("from", today().minusDays(5).toString())
                        .param("to", today().minusDays(2).toString()).with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(twoDaysAgo.getId().toString()))
                .andExpect(jsonPath("$.content[1].id").value(fiveDaysAgo.getId().toString()));

        mockMvc.perform(get("/emails").param("from", today().toString()).with(systemAdmin()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(todays.getId().toString()));
    }

    @Test
    void search_pagination_isClampedToOneHundred() throws Exception {
        for (int index = 0; index < 5; index++) {
            saveEmail(EmailStatus.SENT, "user" + index + "@mail.com", "TicketPurchased", null, null);
        }

        mockMvc.perform(get("/emails").param("page", "1").param("size", "2").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/emails").param("page", "-1").param("size", "500").with(systemAdmin()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content", hasSize(5)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"status=ENVIADO", "referenceId=no-es-uuid", "from=31-10-2026", "page=uno"})
    void search_withInvalidParameter_returnsBadRequestNamingIt(String parameter) throws Exception {
        String[] parts = parameter.split("=");

        mockMvc.perform(get("/emails").param(parts[0], parts[1]).with(systemAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString(parts[0])))
                .andExpect(jsonPath("$.path").value("/emails"));
    }

    @Test
    void summary_countsEveryStatusIncludingZeros() throws Exception {
        saveEmail(EmailStatus.SENT, "a@mail.com", "TicketPurchased", null, null);
        saveEmail(EmailStatus.SENT, "b@mail.com", "TicketPurchased", null, null);
        saveEmail(EmailStatus.FAILED, "c@mail.com", "TicketPurchased", null, null);

        mockMvc.perform(get("/emails/summary").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].total").value(0))
                .andExpect(jsonPath("$[1].status").value("SENT"))
                .andExpect(jsonPath("$[1].total").value(2))
                .andExpect(jsonPath("$[2].status").value("FAILED"))
                .andExpect(jsonPath("$[2].total").value(1))
                .andExpect(jsonPath("$[3].status").value("SKIPPED"))
                .andExpect(jsonPath("$[3].total").value(0));
    }

    @Test
    void getById_returnsDetailWithBody() throws Exception {
        EmailLogEntity email = saveEmail(EmailStatus.SENT, "ana@mail.com", "TicketPurchased", null, null);

        mockMvc.perform(get("/emails/{id}", email.getId()).with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(email.getId().toString()))
                .andExpect(jsonPath("$.eventId").value(email.getEventId().toString()))
                .andExpect(jsonPath("$.recipient").value("ana@mail.com"))
                .andExpect(jsonPath("$.htmlBody").value("<p>Hola</p>"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void getById_unknownEmail_returnsNotFound() throws Exception {
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(get("/emails/{id}", unknown).with(systemAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Correo no encontrado"))
                .andExpect(jsonPath("$.path").value("/emails/" + unknown));
    }

    @Test
    void getById_withMalformedId_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/emails/no-es-uuid").with(systemAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("id")));
    }

    @Test
    void retry_failedEmail_isRequeuedWithZeroAttempts() throws Exception {
        EmailLogEntity failed = saveEmail(EmailStatus.FAILED, "ana@mail.com", "TicketPurchased", null, null);

        mockMvc.perform(post("/emails/{id}/retry", failed.getId()).with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(failed.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0))
                .andExpect(jsonPath("$.nextAttemptAt").isNotEmpty());

        EmailLogEntity saved = emailLogRepository.findById(failed.getId()).orElseThrow();
        assertEquals(EmailStatus.PENDING, saved.getStatus());
        assertEquals(0, saved.getAttempts());
        assertNotNull(saved.getNextAttemptAt());
    }

    @Test
    void retry_emailNotFailed_returnsConflictAndKeepsIt() throws Exception {
        EmailLogEntity sent = saveEmail(EmailStatus.SENT, "ana@mail.com", "TicketPurchased", null, null);

        mockMvc.perform(post("/emails/{id}/retry", sent.getId()).with(systemAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("SENT")));

        EmailLogEntity saved = emailLogRepository.findById(sent.getId()).orElseThrow();
        assertEquals(EmailStatus.SENT, saved.getStatus());
        assertNotNull(saved.getSentAt());
    }

    @Test
    void retry_unknownEmail_returnsNotFound() throws Exception {
        mockMvc.perform(post("/emails/{id}/retry", UUID.randomUUID()).with(systemAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Correo no encontrado"));
    }

    @Test
    void retryFailed_requeuesOnlyFailedEmails() throws Exception {
        EmailLogEntity first = saveEmail(EmailStatus.FAILED, "a@mail.com", "TicketPurchased", null, null);
        EmailLogEntity second = saveEmail(EmailStatus.FAILED, "b@mail.com", "TicketPurchased", null, null);
        EmailLogEntity sent = saveEmail(EmailStatus.SENT, "c@mail.com", "TicketPurchased", null, null);
        EmailLogEntity skipped = saveEmail(EmailStatus.SKIPPED, null, "TicketPurchased", null, null);

        mockMvc.perform(post("/emails/retry-failed").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requeued").value(2));

        assertEquals(EmailStatus.PENDING, emailLogRepository.findById(first.getId()).orElseThrow().getStatus());
        assertEquals(EmailStatus.PENDING, emailLogRepository.findById(second.getId()).orElseThrow().getStatus());
        assertEquals(EmailStatus.SENT, emailLogRepository.findById(sent.getId()).orElseThrow().getStatus());
        assertEquals(EmailStatus.SKIPPED, emailLogRepository.findById(skipped.getId()).orElseThrow().getStatus());
    }

    @Test
    void retryFailed_withNothingFailed_returnsZero() throws Exception {
        saveEmail(EmailStatus.SENT, "c@mail.com", "TicketPurchased", null, null);

        mockMvc.perform(post("/emails/retry-failed").with(systemAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requeued").value(0));
    }
}
