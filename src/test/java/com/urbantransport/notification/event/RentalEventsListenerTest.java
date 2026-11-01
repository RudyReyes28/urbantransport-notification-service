package com.urbantransport.notification.event;

import com.urbantransport.notification.service.PrivateTripService;
import com.urbantransport.notification.service.RentalNotificationService;
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
class RentalEventsListenerTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RENTAL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BRANCH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CUSTOMER_USER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID BUS_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID DRIVER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final LocalDate DEPARTURE_DATE = LocalDate.now(ZONE).plusDays(10);
    private static final Instant OCCURRED_AT = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private RentalNotificationService rentalNotificationService;

    @Mock
    private PrivateTripService privateTripService;

    @Captor
    private ArgumentCaptor<RentalEvent.Payload> rentalCaptor;

    @Captor
    private ArgumentCaptor<RentalAssignedEvent.Payload> assignedCaptor;

    private RentalEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new RentalEventsListener(jsonMapper, rentalNotificationService, privateTripService);
    }

    @Test
    void rentalRequested_isHandledWithConvertedPayload() {
        listener.onEvent(message(EventTypes.RENTAL_REQUESTED, rentalPayload(1200, null)));

        verify(rentalNotificationService).handleRequested(eq(EVENT_ID), eq(OCCURRED_AT), rentalCaptor.capture());
        RentalEvent.Payload payload = rentalCaptor.getValue();
        assertEquals(RENTAL_ID, payload.rentalId());
        assertEquals("ONLINE", payload.channel());
        assertEquals(BRANCH_ID, payload.branchId());
        assertEquals(CUSTOMER_USER_ID, payload.customerUserId());
        assertEquals(DEPARTURE_DATE, payload.departureDate());
        assertNull(payload.returnDate());
        assertEquals(30, payload.passengers());
        assertEquals(0, new BigDecimal("120.5").compareTo(payload.distanceKm()));
        assertEquals(0, new BigDecimal("1200").compareTo(payload.estimatedPrice()));
        assertNull(payload.confirmedPrice());
        verifyNoMoreInteractions(rentalNotificationService);
        verifyNoInteractions(privateTripService);
    }

    @Test
    void rentalQuoted_isHandledWithConfirmedPrice() {
        listener.onEvent(message(EventTypes.RENTAL_QUOTED, rentalPayload(1200, 1350.75)));

        verify(rentalNotificationService).handleQuoted(eq(EVENT_ID), eq(OCCURRED_AT), rentalCaptor.capture());
        assertEquals(0, new BigDecimal("1350.75").compareTo(rentalCaptor.getValue().confirmedPrice()));
        verifyNoMoreInteractions(rentalNotificationService);
        verifyNoInteractions(privateTripService);
    }

    @Test
    void rentalRejected_isHandled() {
        listener.onEvent(message(EventTypes.RENTAL_REJECTED, rentalPayload(1200, null)));

        verify(rentalNotificationService).handleRejected(eq(EVENT_ID), eq(OCCURRED_AT), rentalCaptor.capture());
        assertEquals("Sin buses", rentalCaptor.getValue().rejectionReason());
        verifyNoMoreInteractions(rentalNotificationService);
        verifyNoInteractions(privateTripService);
    }

    @Test
    void rentalPaid_isHandled() {
        listener.onEvent(message(EventTypes.RENTAL_PAID, rentalPayload(1200, 1300)));

        verify(rentalNotificationService).handlePaid(eq(EVENT_ID), eq(OCCURRED_AT), rentalCaptor.capture());
        assertEquals(DEPARTURE_DATE.minusDays(1), rentalCaptor.getValue().paymentDate());
        verifyNoMoreInteractions(rentalNotificationService);
        verifyNoInteractions(privateTripService);
    }

    @Test
    void rentalAssigned_isRecordedWithLegsAsInstants() {
        Instant departure = OCCURRED_AT.plus(5, ChronoUnit.DAYS);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rentalId", RENTAL_ID.toString());
        payload.put("branchId", BRANCH_ID.toString());
        payload.put("busId", BUS_ID.toString());
        payload.put("driverId", DRIVER_ID.toString());
        payload.put("passengers", 30);
        payload.put("distanceKm", 120.5);
        payload.put("customerNit", "1234567");
        payload.put("customerUserId", null);
        payload.put("assignedBy", UUID.randomUUID().toString());
        payload.put("legs", List.of(
                Map.of("originText", "Central", "destinationText", "Xela", "departureAt", departure.toString(),
                        "estimatedArrival", departure.plus(4, ChronoUnit.HOURS).toString()),
                Map.of("originText", "Xela", "destinationText", "Central",
                        "departureAt", departure.plus(1, ChronoUnit.DAYS).toString(),
                        "estimatedArrival", departure.plus(28, ChronoUnit.HOURS).toString())));

        listener.onEvent(message(EventTypes.RENTAL_ASSIGNED, payload));

        verify(privateTripService).recordAssignment(eq(EVENT_ID), eq(OCCURRED_AT), assignedCaptor.capture());
        RentalAssignedEvent.Payload received = assignedCaptor.getValue();
        assertEquals(RENTAL_ID, received.rentalId());
        assertEquals(BUS_ID, received.busId());
        assertEquals(DRIVER_ID, received.driverId());
        assertEquals(30, received.passengers());
        assertNull(received.customerUserId());
        assertEquals(2, received.legs().size());
        assertEquals("Central", received.legs().getFirst().originText());
        assertEquals(departure, received.legs().getFirst().departureAt());
        assertEquals(departure.plus(28, ChronoUnit.HOURS), received.legs().getLast().estimatedArrival());
        verifyNoInteractions(rentalNotificationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PaymentRequested", "RentalCancelled", "RentalCompleted"})
    void otherEvent_isIgnored(String eventType) {
        listener.onEvent(message(eventType, rentalPayload(1200, null)));

        verifyNoInteractions(rentalNotificationService, privateTripService);
    }

    @Test
    void messageWithoutEventType_isIgnored() {
        listener.onEvent(jsonMapper.writeValueAsString(Map.of("eventId", EVENT_ID.toString())));

        verifyNoInteractions(rentalNotificationService, privateTripService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onEvent("{no es json"));

        verifyNoInteractions(rentalNotificationService, privateTripService);
    }

    private static Map<String, Object> rentalPayload(Number estimatedPrice, Number confirmedPrice) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rentalId", RENTAL_ID.toString());
        payload.put("channel", "ONLINE");
        payload.put("branchId", BRANCH_ID.toString());
        payload.put("customerNit", "1234567");
        payload.put("customerUserId", CUSTOMER_USER_ID.toString());
        payload.put("originText", "Central");
        payload.put("destinationText", "Xela");
        payload.put("departureDate", DEPARTURE_DATE.toString());
        payload.put("returnDate", null);
        payload.put("passengers", 30);
        payload.put("distanceKm", 120.5);
        payload.put("estimatedPrice", estimatedPrice);
        payload.put("confirmedPrice", confirmedPrice);
        payload.put("paymentDate", DEPARTURE_DATE.minusDays(1).toString());
        payload.put("rejectionReason", "Sin buses");
        payload.put("status", "REQUESTED");
        return payload;
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", OCCURRED_AT.toString(),
                "aggregateId", RENTAL_ID.toString(),
                "version", 1,
                "payload", payload));
    }
}
