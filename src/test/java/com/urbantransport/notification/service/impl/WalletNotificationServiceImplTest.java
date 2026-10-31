package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationEventProperties;
import com.urbantransport.notification.domain.ProcessedEventEntity;
import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.Tone;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.event.WalletRechargedEvent;
import com.urbantransport.notification.repository.ProcessedEventRepository;
import com.urbantransport.notification.service.EventMailer;
import com.urbantransport.notification.service.MailRequest;
import com.urbantransport.notification.service.RecipientResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletNotificationServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.now(ZONE);
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final NotificationEventProperties PROPERTIES = new NotificationEventProperties(Duration.ofHours(24),
            Duration.ofDays(7), Duration.ofDays(2), Duration.ofDays(2), Duration.ofDays(2), Duration.ofDays(2));

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private RecipientResolver recipientResolver;

    @Mock
    private EventMailer eventMailer;

    @Captor
    private ArgumentCaptor<MailRequest> mailCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEventEntity> processedEventCaptor;

    private WalletNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WalletNotificationServiceImpl(processedEventRepository, recipientResolver, eventMailer,
                PROPERTIES, CLOCK);
    }

    @Nested
    class HandleRecharged {

        @Test
        void recentRecharge_sendsReceiptToCustomer() {
            when(recipientResolver.customerEmail(CUSTOMER_ID)).thenReturn(List.of("ana@mail.com"));

            service.handleRecharged(EVENT_ID, NOW.minusSeconds(60), payload(new BigDecimal("1250.5"), TODAY));

            verify(eventMailer).send(mailCaptor.capture());
            MailRequest mail = mailCaptor.getValue();
            assertEquals(EVENT_ID, mail.eventId());
            assertEquals(EventTypes.WALLET_RECHARGED, mail.eventType());
            assertEquals(CUSTOMER_ID, mail.referenceId());
            assertEquals("wallet-recharged", mail.template());
            assertEquals("Recarga recibida: Q 1,250.50", mail.subject());
            assertEquals(List.of("ana@mail.com"), mail.recipients());
            assertEquals("El cliente no tiene correo en el directorio", mail.skipReason());
            assertEquals(Tone.SUCCESS, mail.view().getTone());
            assertEquals("Q 1,250.50", rowValue(mail, "Monto"));
            assertEquals(String.format("%02d/%02d/%d", TODAY.getDayOfMonth(), TODAY.getMonthValue(), TODAY.getYear()),
                    rowValue(mail, "Fecha de pago"));
            assertMarkedProcessed();
        }

        @Test
        void rechargeWithoutDate_showsDash() {
            when(recipientResolver.customerEmail(CUSTOMER_ID)).thenReturn(List.of());

            service.handleRecharged(EVENT_ID, NOW, payload(BigDecimal.TEN, null));

            verify(eventMailer).send(mailCaptor.capture());
            assertEquals("—", rowValue(mailCaptor.getValue(), "Fecha de pago"));
            assertTrue(mailCaptor.getValue().recipients().isEmpty());
        }

        @Test
        void staleRecharge_isSkippedWithoutLookingUpTheCustomer() {
            service.handleRecharged(EVENT_ID, NOW.minus(3, ChronoUnit.DAYS), payload(BigDecimal.TEN, TODAY));

            verify(eventMailer).send(mailCaptor.capture());
            assertTrue(mailCaptor.getValue().recipients().isEmpty());
            assertNull(mailCaptor.getValue().view());
            assertEquals("Evento de hace más de PT48H", mailCaptor.getValue().skipReason());
            verifyNoInteractions(recipientResolver);
            assertMarkedProcessed();
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            service.handleRecharged(EVENT_ID, NOW, payload(BigDecimal.TEN, TODAY));

            verifyNoInteractions(eventMailer, recipientResolver);
            verify(processedEventRepository, never()).save(any());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.WalletNotificationServiceImplTest#incomplete")
        void incompleteEvent_throwsAndSendsNothing(Instant occurredAt, WalletRechargedEvent.Payload payload) {
            assertThrows(IllegalArgumentException.class, () -> service.handleRecharged(EVENT_ID, occurredAt, payload));

            verifyNoInteractions(eventMailer, recipientResolver);
            verify(processedEventRepository, never()).save(any());
        }
    }

    static Stream<Arguments> incomplete() {
        return Stream.of(
                Arguments.of(null, payload(BigDecimal.TEN, TODAY)),
                Arguments.of(NOW, null),
                Arguments.of(NOW,
                        new WalletRechargedEvent.Payload(null, BigDecimal.TEN, TODAY)),
                Arguments.of(NOW, payload(null, TODAY)));
    }

    private void assertMarkedProcessed() {
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
    }

    private static WalletRechargedEvent.Payload payload(BigDecimal amount, LocalDate paymentDate) {
        return new WalletRechargedEvent.Payload(CUSTOMER_ID, amount, paymentDate);
    }

    private static String rowValue(MailRequest request, String label) {
        return request.view().getRows().stream().filter(row -> row.getLabel().equals(label))
                .map(NoticeRow::getValue).findFirst().orElse(null);
    }
}
