package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationEventProperties;
import com.urbantransport.notification.domain.ProcessedEventEntity;
import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.Tone;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.event.RentalEvent;
import com.urbantransport.notification.repository.ProcessedEventRepository;
import com.urbantransport.notification.service.EventMailer;
import com.urbantransport.notification.service.MailRequest;
import com.urbantransport.notification.service.RecipientResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RentalNotificationServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant RECENT = NOW.minusSeconds(60);
    private static final Instant STALE = NOW.minus(3, ChronoUnit.DAYS);
    private static final LocalDate DEPARTURE = LocalDate.now(ZONE).plusDays(10);
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RENTAL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BRANCH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CUSTOMER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final List<String> ADMINS = List.of("admin@mail.com");
    private static final List<String> CUSTOMER = List.of("cliente@mail.com");
    private static final String NO_CUSTOMER =
            "El alquiler no tiene un cliente con cuenta y correo en el directorio (venta en mostrador)";
    private static final String NO_ADMINS = "No hay administradores activos en el directorio";
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

    private RentalNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RentalNotificationServiceImpl(processedEventRepository, recipientResolver, eventMailer,
                PROPERTIES, CLOCK);
    }

    @Nested
    class HandleRequested {

        @Test
        void onlineRequest_notifiesAdminsAndAcknowledgesCustomer() {
            when(recipientResolver.branchAdmins(BRANCH_ID)).thenReturn(ADMINS);
            when(recipientResolver.userEmail(CUSTOMER_USER_ID)).thenReturn(CUSTOMER);

            service.handleRequested(EVENT_ID, RECENT, payload("ONLINE"));

            verify(eventMailer, times(2)).send(mailCaptor.capture());
            MailRequest admin = mailCaptor.getAllValues().getFirst();
            assertEquals(EVENT_ID, admin.eventId());
            assertEquals(EventTypes.RENTAL_REQUESTED, admin.eventType());
            assertEquals(RENTAL_ID, admin.referenceId());
            assertEquals("rental-requested-admin", admin.template());
            assertEquals("Nueva solicitud de alquiler: Central → Xela", admin.subject());
            assertEquals(ADMINS, admin.recipients());
            assertEquals(NO_ADMINS, admin.skipReason());
            assertEquals("1234567", rowValue(admin, "NIT del cliente"));
            assertEquals("Central → Xela", rowValue(admin, "Ruta"));
            assertEquals(date(DEPARTURE), rowValue(admin, "Salida"));
            assertEquals(date(DEPARTURE.plusDays(2)), rowValue(admin, "Retorno"));
            assertEquals("30", rowValue(admin, "Pasajeros"));
            assertEquals("120.5 km", rowValue(admin, "Distancia"));
            assertEquals("Q 1,200.00", rowValue(admin, "Precio estimado"));
            MailRequest customer = mailCaptor.getAllValues().getLast();
            assertEquals("rental-requested-customer", customer.template());
            assertEquals("Recibimos tu solicitud de alquiler", customer.subject());
            assertEquals(CUSTOMER, customer.recipients());
            assertEquals(NO_CUSTOMER, customer.skipReason());
            assertMarkedProcessed();
        }

        @Test
        void requestWithoutOptionalData_showsDashesAndOneWay() {
            when(recipientResolver.branchAdmins(BRANCH_ID)).thenReturn(ADMINS);
            when(recipientResolver.userEmail(null)).thenReturn(List.of());

            service.handleRequested(EVENT_ID, RECENT, new RentalEvent.Payload(RENTAL_ID, "ONLINE", BRANCH_ID, null,
                    null, "Central", "Xela", DEPARTURE, null, null, null, null, null, null, null, "REQUESTED"));

            verify(eventMailer, times(2)).send(mailCaptor.capture());
            MailRequest admin = mailCaptor.getAllValues().getFirst();
            assertEquals("—", rowValue(admin, "NIT del cliente"));
            assertEquals("Solo ida", rowValue(admin, "Retorno"));
            assertEquals("—", rowValue(admin, "Pasajeros"));
            assertNull(rowValue(admin, "Distancia"));
            assertEquals("—", rowValue(admin, "Precio estimado"));
        }

        @Test
        void counterRequest_sendsNothingButIsMarked() {
            service.handleRequested(EVENT_ID, RECENT, payload("COUNTER"));

            verifyNoInteractions(eventMailer, recipientResolver);
            assertMarkedProcessed();
        }

        @Test
        void staleRequest_isSkipped() {
            service.handleRequested(EVENT_ID, STALE, payload("ONLINE"));

            verify(eventMailer).send(mailCaptor.capture());
            assertSkipped(mailCaptor.getValue(), "rental-requested-admin", EventTypes.RENTAL_REQUESTED);
            verifyNoInteractions(recipientResolver);
            assertMarkedProcessed();
        }
    }

    @Nested
    class HandleQuoted {

        @Test
        void quote_sendsFinalPriceToCustomer() {
            when(recipientResolver.userEmail(CUSTOMER_USER_ID)).thenReturn(CUSTOMER);

            service.handleQuoted(EVENT_ID, RECENT, payload("ONLINE"));

            verify(eventMailer).send(mailCaptor.capture());
            MailRequest mail = mailCaptor.getValue();
            assertEquals(EventTypes.RENTAL_QUOTED, mail.eventType());
            assertEquals("rental-quoted", mail.template());
            assertEquals("Tu alquiler fue cotizado: Q 1,350.75", mail.subject());
            assertEquals(Tone.SUCCESS, mail.view().getTone());
            assertEquals("Q 1,350.75", rowValue(mail, "Precio final"));
            assertEquals(CUSTOMER, mail.recipients());
            assertEquals(NO_CUSTOMER, mail.skipReason());
            assertMarkedProcessed();
        }

        @Test
        void staleQuote_isSkipped() {
            service.handleQuoted(EVENT_ID, STALE, payload("ONLINE"));

            verify(eventMailer).send(mailCaptor.capture());
            assertSkipped(mailCaptor.getValue(), "rental-quoted", EventTypes.RENTAL_QUOTED);
            verifyNoInteractions(recipientResolver);
        }
    }

    @Nested
    class HandleRejected {

        @Test
        void rejection_sendsReasonFirstAndNoPrice() {
            when(recipientResolver.userEmail(CUSTOMER_USER_ID)).thenReturn(CUSTOMER);

            service.handleRejected(EVENT_ID, RECENT, payload("ONLINE"));

            verify(eventMailer).send(mailCaptor.capture());
            MailRequest mail = mailCaptor.getValue();
            assertEquals("rental-rejected", mail.template());
            assertEquals("Tu solicitud de alquiler no pudo atenderse", mail.subject());
            assertEquals(Tone.DANGER, mail.view().getTone());
            assertEquals(new NoticeRow("Motivo", "Sin buses"), mail.view().getRows().getFirst());
            assertNull(rowValue(mail, "Precio estimado"));
            assertNull(rowValue(mail, "Precio final"));
            assertMarkedProcessed();
        }

        @Test
        void rejectionWithoutReason_showsDash() {
            when(recipientResolver.userEmail(CUSTOMER_USER_ID)).thenReturn(CUSTOMER);

            service.handleRejected(EVENT_ID, RECENT, new RentalEvent.Payload(RENTAL_ID, "ONLINE", BRANCH_ID,
                    "1234567", CUSTOMER_USER_ID, "Central", "Xela", DEPARTURE, null, 30, null, null, null, null, null,
                    "REJECTED"));

            verify(eventMailer).send(mailCaptor.capture());
            assertEquals("—", rowValue(mailCaptor.getValue(), "Motivo"));
        }

        @Test
        void staleRejection_isSkipped() {
            service.handleRejected(EVENT_ID, STALE, payload("ONLINE"));

            verify(eventMailer).send(mailCaptor.capture());
            assertSkipped(mailCaptor.getValue(), "rental-rejected", EventTypes.RENTAL_REJECTED);
        }
    }

    @Nested
    class HandlePaid {

        @Test
        void onlinePayment_sendsReceiptAndAsksAdminsToAssign() {
            when(recipientResolver.userEmail(CUSTOMER_USER_ID)).thenReturn(CUSTOMER);
            when(recipientResolver.branchAdmins(BRANCH_ID)).thenReturn(ADMINS);

            service.handlePaid(EVENT_ID, RECENT, payload("ONLINE"));

            verify(eventMailer, times(2)).send(mailCaptor.capture());
            MailRequest customer = mailCaptor.getAllValues().getFirst();
            assertEquals(EventTypes.RENTAL_PAID, customer.eventType());
            assertEquals("rental-paid-customer", customer.template());
            assertEquals("Pago de alquiler recibido: Q 1,350.75", customer.subject());
            assertEquals("Q 1,350.75", rowValue(customer, "Total pagado"));
            assertEquals(date(DEPARTURE.minusDays(1)), rowValue(customer, "Fecha de pago"));
            assertEquals(CUSTOMER, customer.recipients());
            MailRequest admin = mailCaptor.getAllValues().getLast();
            assertEquals("rental-paid-admin", admin.template());
            assertEquals("Alquiler pagado, falta asignar bus y chofer: Central → Xela", admin.subject());
            assertEquals(Tone.WARNING, admin.view().getTone());
            assertEquals(ADMINS, admin.recipients());
            assertMarkedProcessed();
        }

        @Test
        void counterPayment_onlySendsReceipt() {
            when(recipientResolver.userEmail(CUSTOMER_USER_ID)).thenReturn(List.of());

            service.handlePaid(EVENT_ID, RECENT, payload("COUNTER"));

            verify(eventMailer).send(mailCaptor.capture());
            assertEquals("rental-paid-customer", mailCaptor.getValue().template());
            verify(recipientResolver, never()).branchAdmins(any());
        }

        @Test
        void stalePayment_isSkipped() {
            service.handlePaid(EVENT_ID, STALE, payload("ONLINE"));

            verify(eventMailer).send(mailCaptor.capture());
            assertSkipped(mailCaptor.getValue(), "rental-paid-customer", EventTypes.RENTAL_PAID);
            verifyNoInteractions(recipientResolver);
        }
    }

    @Nested
    class CommonRules {

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.RentalNotificationServiceImplTest#handlers")
        void alreadyProcessedEvent_isIgnored(Handler handler) {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            handler.call(service, RECENT, payload("ONLINE"));

            verifyNoInteractions(eventMailer, recipientResolver);
            verify(processedEventRepository, never()).save(any());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.RentalNotificationServiceImplTest#incompleteCases")
        void incompleteEvent_throwsAndSendsNothing(Handler handler, Instant occurredAt, RentalEvent.Payload payload) {
            Executable action = () -> handler.call(service, occurredAt, payload);

            assertThrows(IllegalArgumentException.class, action);

            verifyNoInteractions(eventMailer, recipientResolver);
            verify(processedEventRepository, never()).save(any());
        }
    }

    interface Handler {
        void call(RentalNotificationServiceImpl service, Instant occurredAt, RentalEvent.Payload payload);
    }

    static Stream<Handler> handlers() {
        return Stream.of(
                (s, at, p) -> s.handleRequested(EVENT_ID, at, p),
                (s, at, p) -> s.handleQuoted(EVENT_ID, at, p),
                (s, at, p) -> s.handleRejected(EVENT_ID, at, p),
                (s, at, p) -> s.handlePaid(EVENT_ID, at, p));
    }

    static Stream<Arguments> incompleteCases() {
        return handlers().flatMap(handler -> Stream.of(
                Arguments.of(handler, null, payload("ONLINE")),
                Arguments.of(handler, RECENT, null),
                Arguments.of(handler, RECENT, new RentalEvent.Payload(null, "ONLINE", BRANCH_ID, null, null,
                        "Central", "Xela", DEPARTURE, null, null, null, null, null, null, null, null)),
                Arguments.of(handler, RECENT, new RentalEvent.Payload(RENTAL_ID, "ONLINE", BRANCH_ID, null, null,
                        null, "Xela", DEPARTURE, null, null, null, null, null, null, null, null)),
                Arguments.of(handler, RECENT, new RentalEvent.Payload(RENTAL_ID, "ONLINE", BRANCH_ID, null, null,
                        "Central", null, DEPARTURE, null, null, null, null, null, null, null, null))));
    }

    private void assertMarkedProcessed() {
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
    }

    private static void assertSkipped(MailRequest mail, String template, String eventType) {
        assertEquals(template, mail.template());
        assertEquals(eventType, mail.eventType());
        assertTrue(mail.recipients().isEmpty());
        assertNull(mail.view());
        assertEquals("Evento de hace más de PT48H", mail.skipReason());
        assertFalse(mail.subject().isBlank());
    }

    private static RentalEvent.Payload payload(String channel) {
        return new RentalEvent.Payload(RENTAL_ID, channel, BRANCH_ID, "1234567", CUSTOMER_USER_ID, "Central", "Xela",
                DEPARTURE, DEPARTURE.plusDays(2), 30, new BigDecimal("120.50"), new BigDecimal("1200"),
                new BigDecimal("1350.75"), DEPARTURE.minusDays(1), "Sin buses", "QUOTED");
    }

    private static String date(LocalDate value) {
        return String.format("%02d/%02d/%d", value.getDayOfMonth(), value.getMonthValue(), value.getYear());
    }

    private static String rowValue(MailRequest request, String label) {
        return request.view().getRows().stream().filter(row -> row.getLabel().equals(label))
                .map(NoticeRow::getValue).findFirst().orElse(null);
    }
}
