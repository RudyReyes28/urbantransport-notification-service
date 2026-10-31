package com.urbantransport.notification.job;

import com.urbantransport.notification.config.NotificationDispatchProperties;
import com.urbantransport.notification.repository.EmailLogRepository;
import com.urbantransport.notification.service.EmailDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailDispatchJobTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID FIRST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID THIRD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final NotificationDispatchProperties PROPERTIES = new NotificationDispatchProperties(5,
            Duration.ofSeconds(30), Duration.ofMinutes(10), Duration.ofMinutes(2), 20);

    @Mock
    private EmailLogRepository emailLogRepository;

    @Mock
    private EmailDispatchService emailDispatchService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    private EmailDispatchJob job;

    @BeforeEach
    void setUp() {
        job = new EmailDispatchJob(emailLogRepository, emailDispatchService, PROPERTIES, CLOCK);
    }

    @Test
    void dueEmails_areDispatchedInOrderWithBatchSize() {
        when(emailLogRepository.findDueIds(eq(NOW), pageableCaptor.capture()))
                .thenReturn(List.of(FIRST_ID, SECOND_ID, THIRD_ID));

        job.dispatchDue();

        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(20, pageableCaptor.getValue().getPageSize());
        InOrder order = inOrder(emailDispatchService);
        order.verify(emailDispatchService).dispatch(FIRST_ID);
        order.verify(emailDispatchService).dispatch(SECOND_ID);
        order.verify(emailDispatchService).dispatch(THIRD_ID);
    }

    @Test
    void failingEmail_doesNotStopTheRest() {
        when(emailLogRepository.findDueIds(eq(NOW), pageableCaptor.capture()))
                .thenReturn(List.of(FIRST_ID, SECOND_ID));
        doThrow(new IllegalStateException("BD caida")).when(emailDispatchService).dispatch(FIRST_ID);

        job.dispatchDue();

        verify(emailDispatchService).dispatch(SECOND_ID);
    }

    @Test
    void nothingDue_dispatchesNothing() {
        when(emailLogRepository.findDueIds(eq(NOW), pageableCaptor.capture())).thenReturn(List.of());

        job.dispatchDue();

        verifyNoInteractions(emailDispatchService);
    }
}
