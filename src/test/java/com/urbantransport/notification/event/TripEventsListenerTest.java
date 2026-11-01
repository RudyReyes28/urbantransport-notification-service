package com.urbantransport.notification.event;

import com.urbantransport.notification.service.TripNotificationService;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
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
class TripEventsListenerTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRIP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DRIVER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID RENTAL_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final LocalDateTime DEPARTURE = LocalDateTime.now(ZONE).plusDays(2).truncatedTo(ChronoUnit.MINUTES);
    private static final Instant OCCURRED_AT = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private TripNotificationService tripNotificationService;

    @Captor
    private ArgumentCaptor<TripUpsertEvent.Payload> upsertCaptor;

    @Captor
    private ArgumentCaptor<TripDeletedEvent.Payload> deletedCaptor;

    @Captor
    private ArgumentCaptor<PrivateTripRejectedEvent.Payload> rejectedCaptor;

    private TripEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new TripEventsListener(jsonMapper, tripNotificationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {EventTypes.TRIP_SCHEDULED, EventTypes.TRIP_UPDATED})
    void tripUpsert_isHandledWithItsEventType(String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tripId", TRIP_ID.toString());
        payload.put("type", "REGULAR");
        payload.put("routeId", UUID.randomUUID().toString());
        payload.put("originName", "Central");
        payload.put("destinationName", "Xela");
        payload.put("originText", null);
        payload.put("destinationText", null);
        payload.put("busId", BUS_ID.toString());
        payload.put("busPlate", "P-001");
        payload.put("seatLayout", Map.of("rows", 10, "columns", 4, "aisleAfterColumn", 2));
        payload.put("driverId", DRIVER_ID.toString());
        payload.put("ticketPrice", 45.5);
        payload.put("departureAt", DEPARTURE.toString());
        payload.put("estimatedArrival", DEPARTURE.plusHours(4).toString());
        payload.put("status", "SCHEDULED");

        listener.onEvent(message(eventType, payload));

        verify(tripNotificationService).handleTripUpsert(eq(EVENT_ID), eq(eventType), eq(OCCURRED_AT),
                upsertCaptor.capture());
        TripUpsertEvent.Payload received = upsertCaptor.getValue();
        assertEquals(TRIP_ID, received.tripId());
        assertEquals("REGULAR", received.type());
        assertEquals("Central", received.originName());
        assertNull(received.originText());
        assertEquals(BUS_ID, received.busId());
        assertEquals(DRIVER_ID, received.driverId());
        assertEquals(DEPARTURE, received.departureAt());
        assertEquals(DEPARTURE.plusHours(4), received.estimatedArrival());
        assertEquals("SCHEDULED", received.status());
        verifyNoMoreInteractions(tripNotificationService);
    }

    @Test
    void tripDeleted_isHandledWithRentalAndDriver() {
        listener.onEvent(message(EventTypes.TRIP_DELETED, Map.of("tripId", TRIP_ID.toString(), "type", "PRIVATE",
                "rentalId", RENTAL_ID.toString(), "driverId", DRIVER_ID.toString(), "status", "SCHEDULED")));

        verify(tripNotificationService).handleTripDeleted(eq(EVENT_ID), eq(OCCURRED_AT), deletedCaptor.capture());
        assertEquals(TRIP_ID, deletedCaptor.getValue().tripId());
        assertEquals("PRIVATE", deletedCaptor.getValue().type());
        assertEquals(RENTAL_ID, deletedCaptor.getValue().rentalId());
        assertEquals(DRIVER_ID, deletedCaptor.getValue().driverId());
        verifyNoMoreInteractions(tripNotificationService);
    }

    @Test
    void privateTripRejected_isHandledWithCodeAndReason() {
        listener.onEvent(message(EventTypes.PRIVATE_TRIP_REJECTED, Map.of("rentalId", RENTAL_ID.toString(),
                "busId", BUS_ID.toString(), "driverId", DRIVER_ID.toString(), "code", "BUS_OVERLAP",
                "reason", "El bus ya tiene un viaje")));

        verify(tripNotificationService).handlePrivateTripRejected(eq(EVENT_ID), eq(OCCURRED_AT),
                rejectedCaptor.capture());
        assertEquals(RENTAL_ID, rejectedCaptor.getValue().rentalId());
        assertEquals(BUS_ID, rejectedCaptor.getValue().busId());
        assertEquals(DRIVER_ID, rejectedCaptor.getValue().driverId());
        assertEquals("BUS_OVERLAP", rejectedCaptor.getValue().code());
        assertEquals("El bus ya tiene un viaje", rejectedCaptor.getValue().reason());
        verifyNoMoreInteractions(tripNotificationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TripStarted", "TripCompleted", "TripCancelled"})
    void otherEvent_isIgnored(String eventType) {
        listener.onEvent(message(eventType, Map.of("tripId", TRIP_ID.toString())));

        verifyNoInteractions(tripNotificationService);
    }

    @Test
    void messageWithoutEventType_isIgnored() {
        listener.onEvent(jsonMapper.writeValueAsString(Map.of("eventId", EVENT_ID.toString())));

        verifyNoInteractions(tripNotificationService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onEvent("{no es json"));

        verifyNoInteractions(tripNotificationService);
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", OCCURRED_AT.toString(),
                "aggregateId", TRIP_ID.toString(),
                "version", 1,
                "payload", payload));
    }
}
