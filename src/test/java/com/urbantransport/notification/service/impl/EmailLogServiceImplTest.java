package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.domain.EmailLogEntity;
import com.urbantransport.notification.domain.EmailStatus;
import com.urbantransport.notification.dto.EmailLogDetailResponse;
import com.urbantransport.notification.dto.EmailLogSummaryResponse;
import com.urbantransport.notification.dto.EmailStatusCountResponse;
import com.urbantransport.notification.dto.PageResponse;
import com.urbantransport.notification.exception.ApiException;
import com.urbantransport.notification.repository.EmailLogRepository;
import com.urbantransport.notification.repository.EmailStatusCount;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailLogServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.now(ZONE);
    private static final UUID EMAIL_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REFERENCE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private EmailLogRepository emailLogRepository;

    @Captor
    private ArgumentCaptor<Specification<EmailLogEntity>> specCaptor;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private EmailLogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmailLogServiceImpl(emailLogRepository, CLOCK);
    }

    @Nested
    class Search {

        @Test
        void results_areMappedToSummariesWithPageData() {
            EmailLogEntity email = email(EmailStatus.SENT);
            when(emailLogRepository.findAll(ArgumentMatchers.<Specification<EmailLogEntity>>any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(email), PageRequest.of(0, 20), 1));

            PageResponse<EmailLogSummaryResponse> response = service.search(null, null, null, null, null, null, 0, 20);

            assertEquals(1, response.getTotalElements());
            assertEquals(1, response.getTotalPages());
            assertEquals(0, response.getPage());
            assertEquals(20, response.getSize());
            EmailLogSummaryResponse summary = response.getContent().getFirst();
            assertEquals(EMAIL_ID, summary.getId());
            assertEquals("TicketPurchased", summary.getEventType());
            assertEquals(REFERENCE_ID, summary.getReferenceId());
            assertEquals("ana@mail.com", summary.getRecipient());
            assertEquals(EmailStatus.SENT, summary.getStatus());
            assertEquals(NOW, summary.getCreatedAt());
        }

        @ParameterizedTest
        @CsvSource({"0, 20, 0, 20", "-3, 20, 0, 20", "2, 0, 2, 1", "1, -5, 1, 1", "0, 500, 0, 100", "4, 100, 4, 100"})
        void pagination_isClampedAndSortedByCreatedAtDesc(int page, int size, int expectedPage, int expectedSize) {
            when(emailLogRepository.findAll(ArgumentMatchers.<Specification<EmailLogEntity>>any(),
                    pageableCaptor.capture())).thenReturn(new PageImpl<>(List.of()));

            service.search(null, null, null, null, null, null, page, size);

            Pageable pageable = pageableCaptor.getValue();
            assertEquals(expectedPage, pageable.getPageNumber());
            assertEquals(expectedSize, pageable.getPageSize());
            assertEquals(Sort.by(Sort.Direction.DESC, "createdAt"), pageable.getSort());
        }

        @Test
        void withoutFilters_buildsAnEmptyConjunction() {
            Criteria criteria = new Criteria();

            criteria.apply(captureSpec(null, null, null, null, null, null));

            verify(criteria.cb).and();
            verify(criteria.cb, never()).equal(any(), any(Object.class));
            verify(criteria.cb, never()).like(any(), anyString(), anyChar());
            verifyNoInteractions(criteria.root);
        }

        @Test
        void blankTextFilters_areIgnored() {
            Criteria criteria = new Criteria();

            criteria.apply(captureSpec(null, "   ", " ", null, null, null));

            verify(criteria.cb).and();
            verifyNoInteractions(criteria.root);
        }

        @Test
        void allFilters_buildOnePredicateEach() {
            Criteria criteria = new Criteria();
            Path<EmailStatus> statusPath = criteria.path("status");
            Path<String> recipientPath = criteria.path("recipient");
            Path<String> eventTypePath = criteria.path("eventType");
            Path<UUID> referencePath = criteria.path("referenceId");
            criteria.path("createdAt");
            Expression<String> lowered = mockExpression();
            doReturn(lowered).when(criteria.cb).lower(any());
            Predicate statusPredicate = criteria.equalReturns(statusPath, EmailStatus.FAILED);
            Predicate eventTypePredicate = criteria.equalReturns(eventTypePath, "TicketPurchased");
            Predicate referencePredicate = criteria.equalReturns(referencePath, REFERENCE_ID);
            Predicate likePredicate = mock(Predicate.class);
            when(criteria.cb.like(lowered, "%ana\\_lopez\\%\\\\x%", '\\')).thenReturn(likePredicate);
            Predicate fromPredicate = mock(Predicate.class);
            Instant fromInstant = TODAY.minusDays(7).atStartOfDay(ZONE).toInstant();
            Instant toInstant = TODAY.plusDays(1).atStartOfDay(ZONE).toInstant();
            doReturn(fromPredicate).when(criteria.cb).greaterThanOrEqualTo(ArgumentMatchers.<Expression<Instant>>any(),
                    ArgumentMatchers.eq(fromInstant));
            Predicate toPredicate = mock(Predicate.class);
            doReturn(toPredicate).when(criteria.cb).lessThan(ArgumentMatchers.<Expression<Instant>>any(),
                    ArgumentMatchers.eq(toInstant));

            criteria.apply(captureSpec(EmailStatus.FAILED, " Ana_Lopez%\\X ", " TicketPurchased ", REFERENCE_ID,
                    TODAY.minusDays(7), TODAY));

            verify(criteria.cb).lower(recipientPath);
            verify(criteria.cb).greaterThanOrEqualTo(ArgumentMatchers.<Expression<Instant>>any(),
                    ArgumentMatchers.eq(fromInstant));
            verify(criteria.cb).lessThan(ArgumentMatchers.<Expression<Instant>>any(), ArgumentMatchers.eq(toInstant));
            verify(criteria.root, times(2)).get("createdAt");
            verify(criteria.cb).and(statusPredicate, likePredicate, eventTypePredicate, referencePredicate,
                    fromPredicate, toPredicate);
        }

        private Specification<EmailLogEntity> captureSpec(EmailStatus status, String recipient, String eventType,
                                                          UUID referenceId, LocalDate from, LocalDate to) {
            when(emailLogRepository.findAll(specCaptor.capture(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            service.search(status, recipient, eventType, referenceId, from, to, 0, 20);
            return specCaptor.getValue();
        }
    }

    @Nested
    class GetById {

        @Test
        void existingEmail_returnsDetailWithBody() {
            when(emailLogRepository.findById(EMAIL_ID)).thenReturn(Optional.of(email(EmailStatus.SENT)));

            EmailLogDetailResponse detail = service.getById(EMAIL_ID);

            assertEquals(EMAIL_ID, detail.getId());
            assertEquals(EVENT_ID, detail.getEventId());
            assertEquals("<p>Hola</p>", detail.getHtmlBody());
            assertEquals(EmailStatus.SENT, detail.getStatus());
            assertEquals(NOW, detail.getSentAt());
        }

        @Test
        void unknownEmail_throwsNotFound() {
            when(emailLogRepository.findById(EMAIL_ID)).thenReturn(Optional.empty());

            assertApiError(HttpStatus.NOT_FOUND, "Correo no encontrado", () -> service.getById(EMAIL_ID));
        }
    }

    @Nested
    class Summary {

        @Test
        void everyStatusIsListedInOrderIncludingZeros() {
            when(emailLogRepository.countByStatus()).thenReturn(List.of(count(EmailStatus.SENT, 7),
                    count(EmailStatus.FAILED, 2)));

            List<EmailStatusCountResponse> summary = service.summary();

            assertEquals(List.of(EmailStatus.PENDING, EmailStatus.SENT, EmailStatus.FAILED, EmailStatus.SKIPPED),
                    summary.stream().map(EmailStatusCountResponse::getStatus).toList());
            assertEquals(List.of(0L, 7L, 2L, 0L), summary.stream().map(EmailStatusCountResponse::getTotal).toList());
        }

        @Test
        void emptyLog_returnsAllZeros() {
            when(emailLogRepository.countByStatus()).thenReturn(List.of());

            assertEquals(List.of(0L, 0L, 0L, 0L),
                    service.summary().stream().map(EmailStatusCountResponse::getTotal).toList());
        }
    }

    @Nested
    class Retry {

        @Test
        void failedEmail_isRequeuedAndReturned() {
            when(emailLogRepository.requeue(EMAIL_ID, NOW)).thenReturn(1);
            when(emailLogRepository.findById(EMAIL_ID)).thenReturn(Optional.of(email(EmailStatus.PENDING)));

            EmailLogSummaryResponse response = service.retry(EMAIL_ID);

            assertEquals(EMAIL_ID, response.getId());
            assertEquals(EmailStatus.PENDING, response.getStatus());
        }

        @ParameterizedTest
        @EnumSource(value = EmailStatus.class, names = {"PENDING", "SENT", "SKIPPED"})
        void emailInOtherStatus_throwsConflictWithCurrentStatus(EmailStatus status) {
            when(emailLogRepository.requeue(EMAIL_ID, NOW)).thenReturn(0);
            when(emailLogRepository.findById(EMAIL_ID)).thenReturn(Optional.of(email(status)));

            assertApiError(HttpStatus.CONFLICT,
                    "Solo se pueden reintentar correos FAILED (estado actual: " + status + ")",
                    () -> service.retry(EMAIL_ID));
        }

        @Test
        void unknownEmail_throwsNotFound() {
            when(emailLogRepository.requeue(EMAIL_ID, NOW)).thenReturn(0);
            when(emailLogRepository.findById(EMAIL_ID)).thenReturn(Optional.empty());

            assertApiError(HttpStatus.NOT_FOUND, "Correo no encontrado", () -> service.retry(EMAIL_ID));
        }
    }

    @Nested
    class RetryAllFailed {

        @Test
        void returnsHowManyWereRequeued() {
            when(emailLogRepository.requeueAllFailed(NOW)).thenReturn(3);

            assertEquals(3, service.retryAllFailed());
        }
    }

    private static void assertApiError(HttpStatus status, String message, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(message, exception.getMessage());
    }

    private static EmailStatusCount count(EmailStatus status, long total) {
        return new EmailStatusCount() {
            @Override
            public EmailStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Expression<T> mockExpression() {
        return mock(Expression.class);
    }

    private static EmailLogEntity email(EmailStatus status) {
        return EmailLogEntity.builder()
                .id(EMAIL_ID)
                .eventId(EVENT_ID)
                .eventType("TicketPurchased")
                .referenceId(REFERENCE_ID)
                .recipient("ana@mail.com")
                .template("ticket-purchased")
                .subject("Asunto")
                .body("<p>Hola</p>")
                .status(status)
                .attempts(1)
                .createdAt(NOW)
                .sentAt(NOW)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static final class Criteria {

        private final Root<EmailLogEntity> root = mock(Root.class);
        private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
        private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

        private <T> Path<T> path(String attribute) {
            Path<T> path = mock(Path.class);
            doReturn(path).when(root).get(attribute);
            return path;
        }

        private Predicate equalReturns(Path<?> path, Object value) {
            Predicate predicate = mock(Predicate.class);
            when(cb.equal(path, value)).thenReturn(predicate);
            return predicate;
        }

        private void apply(Specification<EmailLogEntity> spec) {
            spec.toPredicate(root, query, cb);
        }
    }
}
