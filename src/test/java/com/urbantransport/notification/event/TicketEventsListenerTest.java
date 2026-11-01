package com.urbantransport.notification.event;

import com.urbantransport.notification.service.TicketNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class TicketEventsListenerTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TRIP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TICKET_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID BRANCH_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final LocalDate TODAY = LocalDate.now(ZONE);
    private static final LocalDateTime DEPARTURE = TODAY.plusDays(3).atTime(6, 0);
    private static final Instant OCCURRED_AT = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private TicketNotificationService ticketNotificationService;

    @Captor
    private ArgumentCaptor<TicketPurchasedEvent.Payload> purchasedCaptor;

    @Captor
    private ArgumentCaptor<TicketCancelledEvent.Payload> cancelledCaptor;

    private TicketEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new TicketEventsListener(jsonMapper, ticketNotificationService);
    }

    @Test
    void ticketPurchased_isHandledWithTripAndTickets() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", ORDER_ID.toString());
        payload.put("channel", "ONLINE");
        payload.put("branchId", null);
        payload.put("customerNit", "1234567");
        payload.put("buyerEmail", "ana@mail.com");
        payload.put("paymentDate", TODAY.toString());
        payload.put("total", 90);
        payload.put("trip", Map.of("tripId", TRIP_ID.toString(), "originName", "Central",
                "destinationName", "Xela", "departureAt", DEPARTURE.toString(), "busPlate", "P-001"));
        payload.put("tickets", List.of(
                Map.of("ticketId", TICKET_ID.toString(), "seatNumber", 1, "seatLabel", "1A", "pricePaid", 45),
                Map.of("ticketId", UUID.randomUUID().toString(), "seatNumber", 2, "seatLabel", "1B",
                        "pricePaid", 45.5)));

        listener.onEvent(message(EventTypes.TICKET_PURCHASED, payload));

        verify(ticketNotificationService).handleTicketPurchased(eq(EVENT_ID), eq(OCCURRED_AT),
                purchasedCaptor.capture());
        TicketPurchasedEvent.Payload received = purchasedCaptor.getValue();
        assertEquals(ORDER_ID, received.orderId());
        assertNull(received.branchId());
        assertEquals(TODAY, received.paymentDate());
        assertEquals(0, new BigDecimal("90").compareTo(received.total()));
        assertEquals(TRIP_ID, received.trip().tripId());
        assertEquals(DEPARTURE, received.trip().departureAt());
        assertEquals("P-001", received.trip().busPlate());
        assertEquals(2, received.tickets().size());
        assertEquals(TICKET_ID, received.tickets().getFirst().ticketId());
        assertEquals(1, received.tickets().getFirst().seatNumber());
        assertEquals(0, new BigDecimal("45.5").compareTo(received.tickets().getLast().pricePaid()));
        verifyNoMoreInteractions(ticketNotificationService);
    }

    @Test
    void ticketCancelled_isHandledWithRefundData() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticketId", TICKET_ID.toString());
        payload.put("orderId", ORDER_ID.toString());
        payload.put("tripId", TRIP_ID.toString());
        payload.put("seatNumber", 5);
        payload.put("seatLabel", "2A");
        payload.put("channel", "COUNTER");
        payload.put("branchId", BRANCH_ID.toString());
        payload.put("customerNit", "CF");
        payload.put("buyerEmail", null);
        payload.put("pricePaid", 45.25);
        payload.put("reason", "TRIP_DELETED");
        payload.put("refundMethod", "CASH");
        payload.put("originName", "Central");
        payload.put("destinationName", "Xela");
        payload.put("departureAt", DEPARTURE.toString());

        listener.onEvent(message(EventTypes.TICKET_CANCELLED, payload));

        verify(ticketNotificationService).handleTicketCancelled(eq(EVENT_ID), eq(OCCURRED_AT),
                cancelledCaptor.capture());
        TicketCancelledEvent.Payload received = cancelledCaptor.getValue();
        assertEquals(TICKET_ID, received.ticketId());
        assertEquals(5, received.seatNumber());
        assertEquals(BRANCH_ID, received.branchId());
        assertNull(received.buyerEmail());
        assertEquals(0, new BigDecimal("45.25").compareTo(received.pricePaid()));
        assertEquals("CASH", received.refundMethod());
        assertEquals(DEPARTURE, received.departureAt());
        verifyNoMoreInteractions(ticketNotificationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PaymentRequested", "RefundRequested", "OrderExpired"})
    void otherEvent_isIgnored(String eventType) {
        listener.onEvent(message(eventType, Map.of("orderId", ORDER_ID.toString())));

        verifyNoInteractions(ticketNotificationService);
    }

    @Test
    void messageWithoutEventType_isIgnored() {
        listener.onEvent(jsonMapper.writeValueAsString(Map.of("eventId", EVENT_ID.toString())));

        verifyNoInteractions(ticketNotificationService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onEvent("{no es json"));

        verifyNoInteractions(ticketNotificationService);
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", OCCURRED_AT.toString(),
                "aggregateId", ORDER_ID.toString(),
                "version", 1,
                "payload", payload));
    }
}
