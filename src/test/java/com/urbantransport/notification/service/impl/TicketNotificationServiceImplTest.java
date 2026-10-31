package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationEventProperties;
import com.urbantransport.notification.domain.ProcessedEventEntity;
import com.urbantransport.notification.domain.Role;
import com.urbantransport.notification.domain.UserDirectoryEntity;
import com.urbantransport.notification.dto.TicketLineView;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.event.TicketCancelledEvent;
import com.urbantransport.notification.event.TicketPurchasedEvent;
import com.urbantransport.notification.repository.ProcessedEventRepository;
import com.urbantransport.notification.repository.UserDirectoryRepository;
import com.urbantransport.notification.service.EmailQueueService;
import com.urbantransport.notification.service.EmailRenderer;
import com.urbantransport.notification.service.EmailRequest;
import com.urbantransport.notification.service.TripPassengerService;
import com.urbantransport.notification.util.EmailFormats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketNotificationServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant RECENT = NOW.minusSeconds(60);
    private static final LocalDate TODAY = LocalDate.now(ZONE);
    private static final LocalDateTime DEPARTURE = TODAY.plusDays(3).atTime(6, 0);
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("abcdef12-2222-2222-2222-222222222222");
    private static final UUID TRIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TICKET_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID BRANCH_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String HTML = "<html>correo</html>";
    private static final NotificationEventProperties PROPERTIES = new NotificationEventProperties(Duration.ofHours(24),
            Duration.ofDays(7), Duration.ofDays(2), Duration.ofDays(2), Duration.ofDays(2), Duration.ofDays(2));

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private UserDirectoryRepository userDirectoryRepository;

    @Mock
    private EmailQueueService emailQueueService;

    @Mock
    private EmailRenderer emailRenderer;

    @Mock
    private TripPassengerService tripPassengerService;

    @Captor
    private ArgumentCaptor<EmailRequest> requestCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> variablesCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEventEntity> processedEventCaptor;

    private TicketNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TicketNotificationServiceImpl(processedEventRepository, userDirectoryRepository,
                emailQueueService, emailRenderer, PROPERTIES, CLOCK, tripPassengerService);
    }

    @Nested
    class HandleTicketPurchased {

        @Test
        void purchaseWithEmail_recordsPassengersAndEnqueuesConfirmation() {
            TicketPurchasedEvent.Payload payload = purchase("ONLINE", " Ana@Mail.com ");
            when(emailRenderer.render(eq("ticket-purchased"), variablesCaptor.capture())).thenReturn(HTML);

            service.handleTicketPurchased(EVENT_ID, RECENT, payload);

            verify(tripPassengerService).record(payload);
            Map<String, Object> variables = variablesCaptor.getValue();
            assertEquals("1234567", variables.get("customerNit"));
            assertEquals("Central", variables.get("origin"));
            assertEquals("Xela", variables.get("destination"));
            assertEquals(EmailFormats.longDateTime(DEPARTURE), variables.get("departure"));
            assertEquals("P-001", variables.get("busPlate"));
            assertEquals("ABCDEF12", variables.get("orderCode"));
            assertEquals(List.of(new TicketLineView("1A", "Q 45.00"), new TicketLineView("1B", "Q 45.50")),
                    variables.get("tickets"));
            assertEquals("Q 90.50", variables.get("total"));
            assertEquals(EmailFormats.date(TODAY), variables.get("paymentDate"));
            verify(emailQueueService).enqueue(requestCaptor.capture());
            EmailRequest request = requestCaptor.getValue();
            assertEquals(EVENT_ID, request.eventId());
            assertEquals(EventTypes.TICKET_PURCHASED, request.eventType());
            assertEquals(ORDER_ID, request.referenceId());
            assertEquals("ana@mail.com", request.recipient());
            assertEquals("ticket-purchased", request.template());
            assertEquals("Tu compra de boletos: Central → Xela (" + EmailFormats.shortDateTime(DEPARTURE) + ")",
                    request.subject());
            assertEquals(HTML, request.htmlBody());
            verify(emailQueueService, never()).skip(any(), any(), any(), any(), any(), any());
            assertMarkedProcessed();
        }

        @ParameterizedTest
        @CsvSource(value = {"COUNTER, NULL, Venta en ventanilla: no hay correo del cliente",
                "COUNTER, '  ', Venta en ventanilla: no hay correo del cliente",
                "ONLINE, NULL, La compra no trae correo del comprador"}, nullValues = "NULL")
        void purchaseWithoutEmail_isSkippedWithReason(String channel, String email, String reason) {
            TicketPurchasedEvent.Payload payload = purchase(channel, email);

            service.handleTicketPurchased(EVENT_ID, RECENT, payload);

            verify(tripPassengerService).record(payload);
            verify(emailQueueService).skip(eq(EVENT_ID), eq(EventTypes.TICKET_PURCHASED), eq(ORDER_ID),
                    eq("ticket-purchased"), anyString(), eq(reason));
            verify(emailQueueService, never()).enqueue(any());
            verifyNoInteractions(emailRenderer);
            assertMarkedProcessed();
        }

        @Test
        void stalePurchase_isSkippedButPassengersAreRecorded() {
            TicketPurchasedEvent.Payload payload = purchase("ONLINE", "ana@mail.com");

            service.handleTicketPurchased(EVENT_ID, NOW.minus(25, ChronoUnit.HOURS), payload);

            verify(tripPassengerService).record(payload);
            verify(emailQueueService).skip(eq(EVENT_ID), eq(EventTypes.TICKET_PURCHASED), eq(ORDER_ID),
                    eq("ticket-purchased"), anyString(), eq("Compra de hace más de PT24H"));
            verifyNoInteractions(emailRenderer);
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            service.handleTicketPurchased(EVENT_ID, RECENT, purchase("ONLINE", "ana@mail.com"));

            verifyNoInteractions(tripPassengerService, emailQueueService, emailRenderer);
            verify(processedEventRepository, never()).save(any());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.TicketNotificationServiceImplTest#incompletePurchases")
        void incompletePurchase_throwsAndSavesNothing(Instant occurredAt, TicketPurchasedEvent.Payload payload) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.handleTicketPurchased(EVENT_ID, occurredAt, payload));

            verifyNoInteractions(tripPassengerService, emailQueueService, emailRenderer);
            verify(processedEventRepository, never()).save(any());
        }
    }

    @Nested
    class HandleTicketCancelled {

        @ParameterizedTest
        @CsvSource({"WALLET, TRIP_DELETED, El viaje fue cancelado", "WALLET, CUSTOMER_REQUEST, El boleto fue cancelado"})
        void walletRefund_notifiesBuyer(String refundMethod, String reason, String reasonLabel) {
            when(emailRenderer.render(eq("ticket-cancelled-wallet"), variablesCaptor.capture())).thenReturn(HTML);

            service.handleTicketCancelled(EVENT_ID, RECENT, cancellation(refundMethod, " Ana@Mail.com ", reason));

            verify(tripPassengerService).remove(TICKET_ID);
            Map<String, Object> variables = variablesCaptor.getValue();
            assertEquals("2A", variables.get("seat"));
            assertEquals("Q 45.00", variables.get("amount"));
            assertEquals("ABCDEF12", variables.get("orderCode"));
            assertEquals(reasonLabel, variables.get("reason"));
            assertEquals(EmailFormats.longDateTime(DEPARTURE), variables.get("departure"));
            verify(emailQueueService).enqueue(requestCaptor.capture());
            EmailRequest request = requestCaptor.getValue();
            assertEquals(EventTypes.TICKET_CANCELLED, request.eventType());
            assertEquals(TICKET_ID, request.referenceId());
            assertEquals("ana@mail.com", request.recipient());
            assertEquals("ticket-cancelled-wallet", request.template());
            assertEquals("Tu boleto fue cancelado: asiento 2A, Central → Xela", request.subject());
            verifyNoInteractions(userDirectoryRepository);
            assertMarkedProcessed();
        }

        @Test
        void walletRefundWithoutEmail_isSkipped() {
            service.handleTicketCancelled(EVENT_ID, RECENT, cancellation("WALLET", null, "TRIP_DELETED"));

            verify(emailQueueService).skip(EVENT_ID, EventTypes.TICKET_CANCELLED, TICKET_ID, "ticket-cancelled-wallet",
                    "Tu boleto fue cancelado: asiento 2A, Central → Xela", "La compra no trae correo del comprador");
            verify(emailQueueService, never()).enqueue(any());
            verifyNoInteractions(emailRenderer);
        }

        @Test
        void cashRefund_notifiesEachBranchAdminWithOneRender() {
            when(userDirectoryRepository.findByBranchIdAndRoleAndActiveTrue(BRANCH_ID, Role.BRANCH_ADMIN))
                    .thenReturn(List.of(user("a@mail.com"), user("b@mail.com"), user("a@mail.com")));
            when(emailRenderer.render(eq("ticket-cancelled-cash"), any())).thenReturn(HTML);

            service.handleTicketCancelled(EVENT_ID, RECENT, cancellation("CASH", "ana@mail.com", "TRIP_DELETED"));

            verify(emailRenderer, times(1)).render(eq("ticket-cancelled-cash"), any());
            verify(emailQueueService, times(2)).enqueue(requestCaptor.capture());
            assertEquals(List.of("a@mail.com", "b@mail.com"),
                    requestCaptor.getAllValues().stream().map(EmailRequest::recipient).toList());
            assertEquals("Reembolso en efectivo pendiente: asiento 2A, Central → Xela",
                    requestCaptor.getValue().subject());
            verify(userDirectoryRepository, never()).findByRoleAndActiveTrue(any());
        }

        @Test
        void cashRefundWithoutBranchAdmins_fallsBackToSystemAdmins() {
            when(userDirectoryRepository.findByBranchIdAndRoleAndActiveTrue(BRANCH_ID, Role.BRANCH_ADMIN))
                    .thenReturn(List.of());
            when(userDirectoryRepository.findByRoleAndActiveTrue(Role.SYSTEM_ADMIN))
                    .thenReturn(List.of(user("root@mail.com")));
            when(emailRenderer.render(eq("ticket-cancelled-cash"), any())).thenReturn(HTML);

            service.handleTicketCancelled(EVENT_ID, RECENT, cancellation("CASH", null, "TRIP_DELETED"));

            verify(emailQueueService).enqueue(requestCaptor.capture());
            assertEquals("root@mail.com", requestCaptor.getValue().recipient());
        }

        @Test
        void cashRefundWithoutBranch_goesToSystemAdmins() {
            when(userDirectoryRepository.findByRoleAndActiveTrue(Role.SYSTEM_ADMIN))
                    .thenReturn(List.of(user("root@mail.com")));
            when(emailRenderer.render(eq("ticket-cancelled-cash"), any())).thenReturn(HTML);
            TicketCancelledEvent.Payload payload = new TicketCancelledEvent.Payload(TICKET_ID, ORDER_ID, TRIP_ID, 5,
                    "2A", "COUNTER", null, "CF", null, new BigDecimal("45"), "TRIP_DELETED", "CASH", "Central",
                    "Xela", DEPARTURE);

            service.handleTicketCancelled(EVENT_ID, RECENT, payload);

            verify(userDirectoryRepository, never()).findByBranchIdAndRoleAndActiveTrue(any(), any());
            verify(emailQueueService).enqueue(any());
        }

        @Test
        void cashRefundWithNobodyToNotify_isSkipped() {
            when(userDirectoryRepository.findByBranchIdAndRoleAndActiveTrue(BRANCH_ID, Role.BRANCH_ADMIN))
                    .thenReturn(List.of());
            when(userDirectoryRepository.findByRoleAndActiveTrue(Role.SYSTEM_ADMIN)).thenReturn(List.of());

            service.handleTicketCancelled(EVENT_ID, RECENT, cancellation("CASH", null, "TRIP_DELETED"));

            verify(emailQueueService).skip(EVENT_ID, EventTypes.TICKET_CANCELLED, TICKET_ID, "ticket-cancelled-cash",
                    "Reembolso en efectivo pendiente: asiento 2A, Central → Xela",
                    "No hay administradores activos en el directorio");
            verify(emailQueueService, never()).enqueue(any());
            verifyNoInteractions(emailRenderer);
            assertMarkedProcessed();
        }

        @Test
        void staleCancellation_isSkippedButPassengerIsRemoved() {
            service.handleTicketCancelled(EVENT_ID, NOW.minus(8, ChronoUnit.DAYS),
                    cancellation("CASH", null, "TRIP_DELETED"));

            verify(tripPassengerService).remove(TICKET_ID);
            verify(emailQueueService).skip(eq(EVENT_ID), eq(EventTypes.TICKET_CANCELLED), eq(TICKET_ID),
                    eq("ticket-cancelled-cash"), anyString(), eq("Cancelación de hace más de PT168H"));
            verifyNoInteractions(emailRenderer, userDirectoryRepository);
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            service.handleTicketCancelled(EVENT_ID, RECENT, cancellation("WALLET", "ana@mail.com", "TRIP_DELETED"));

            verifyNoInteractions(tripPassengerService, emailQueueService, emailRenderer);
            verify(processedEventRepository, never()).save(any());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.TicketNotificationServiceImplTest#incompleteCancellations")
        void incompleteCancellation_throwsAndSavesNothing(Instant occurredAt, TicketCancelledEvent.Payload payload) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.handleTicketCancelled(EVENT_ID, occurredAt, payload));

            verifyNoInteractions(tripPassengerService, emailQueueService, emailRenderer);
            verify(processedEventRepository, never()).save(any());
        }
    }

    static Stream<Arguments> incompletePurchases() {
        TicketPurchasedEvent.Trip trip = new TicketPurchasedEvent.Trip(TRIP_ID, "Central", "Xela", DEPARTURE, "P-001");
        List<TicketPurchasedEvent.Ticket> tickets = tickets();
        return Stream.of(
                Arguments.of(null, purchase("ONLINE", "ana@mail.com")),
                Arguments.of(RECENT, null),
                Arguments.of(RECENT, purchase(null, trip, tickets)),
                Arguments.of(RECENT, purchase(ORDER_ID, null, tickets)),
                Arguments.of(RECENT, purchase(ORDER_ID, new TicketPurchasedEvent.Trip(TRIP_ID, "Central", "Xela",
                        null, "P-001"), tickets)),
                Arguments.of(RECENT, purchase(ORDER_ID, trip, null)),
                Arguments.of(RECENT, purchase(ORDER_ID, trip, List.of())));
    }

    static Stream<Arguments> incompleteCancellations() {
        return Stream.of(
                Arguments.of(null, cancellation("WALLET", "ana@mail.com", "TRIP_DELETED")),
                Arguments.of(RECENT, null),
                Arguments.of(RECENT, new TicketCancelledEvent.Payload(null, ORDER_ID, TRIP_ID, 5, "2A", "ONLINE",
                        BRANCH_ID, "CF", null, BigDecimal.TEN, null, "WALLET", "Central", "Xela", DEPARTURE)),
                Arguments.of(RECENT, new TicketCancelledEvent.Payload(TICKET_ID, null, TRIP_ID, 5, "2A", "ONLINE",
                        BRANCH_ID, "CF", null, BigDecimal.TEN, null, "WALLET", "Central", "Xela", DEPARTURE)),
                Arguments.of(RECENT, new TicketCancelledEvent.Payload(TICKET_ID, ORDER_ID, TRIP_ID, 5, "2A", "ONLINE",
                        BRANCH_ID, "CF", null, BigDecimal.TEN, null, "WALLET", "Central", "Xela", null)));
    }

    private void assertMarkedProcessed() {
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
    }

    private static TicketPurchasedEvent.Payload purchase(String channel, String email) {
        return new TicketPurchasedEvent.Payload(ORDER_ID, channel, BRANCH_ID, "1234567", email, TODAY,
                new BigDecimal("90.50"), new TicketPurchasedEvent.Trip(TRIP_ID, "Central", "Xela", DEPARTURE, "P-001"),
                tickets());
    }

    private static TicketPurchasedEvent.Payload purchase(UUID orderId, TicketPurchasedEvent.Trip trip,
                                                         List<TicketPurchasedEvent.Ticket> tickets) {
        return new TicketPurchasedEvent.Payload(orderId, "ONLINE", null, "1234567", "ana@mail.com", TODAY,
                new BigDecimal("90.50"), trip, tickets);
    }

    private static List<TicketPurchasedEvent.Ticket> tickets() {
        return List.of(new TicketPurchasedEvent.Ticket(TICKET_ID, 1, "1A", new BigDecimal("45")),
                new TicketPurchasedEvent.Ticket(UUID.randomUUID(), 2, "1B", new BigDecimal("45.5")));
    }

    private static TicketCancelledEvent.Payload cancellation(String refundMethod, String email, String reason) {
        return new TicketCancelledEvent.Payload(TICKET_ID, ORDER_ID, TRIP_ID, 5, "2A", "ONLINE", BRANCH_ID, "1234567",
                email, new BigDecimal("45.00"), reason, refundMethod, "Central", "Xela", DEPARTURE);
    }

    private static UserDirectoryEntity user(String email) {
        return UserDirectoryEntity.builder()
                .userId(UUID.randomUUID())
                .email(email)
                .fullName("Admin")
                .role(Role.BRANCH_ADMIN)
                .branchId(BRANCH_ID)
                .active(true)
                .lastEventAt(NOW)
                .build();
    }
}
