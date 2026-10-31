package com.urbantransport.notification.event;

import com.urbantransport.notification.service.DriverDirectoryService;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FleetEventsListenerTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DRIVER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID BRANCH_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final LocalDate EXPIRES_ON = LocalDate.now(ZONE).plusDays(30);
    private static final Instant OCCURRED_AT = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private DriverDirectoryService driverDirectoryService;

    @Captor
    private ArgumentCaptor<DriverEvent> eventCaptor;

    private FleetEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new FleetEventsListener(jsonMapper, driverDirectoryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {EventTypes.DRIVER_REGISTERED, EventTypes.DRIVER_UPDATED, EventTypes.DRIVER_DEACTIVATED})
    void driverEvent_isAppliedWithConvertedPayload(String eventType) {
        listener.onEvent(message(eventType, payload(USER_ID, true)));

        verify(driverDirectoryService).applyDriverEvent(eventCaptor.capture());
        DriverEvent event = eventCaptor.getValue();
        assertEquals(EVENT_ID, event.eventId());
        assertEquals(eventType, event.eventType());
        assertEquals(OCCURRED_AT, event.occurredAt());
        assertEquals(DRIVER_ID, event.payload().driverId());
        assertEquals(USER_ID, event.payload().userId());
        assertEquals("Juan Perez", event.payload().fullName());
        assertEquals("LIC-001", event.payload().licenseNumber());
        assertEquals(EXPIRES_ON, event.payload().licenseExpiresOn());
        assertEquals("juan@mail.com", event.payload().email());
        assertEquals(BRANCH_ID, event.payload().branchId());
        assertTrue(event.payload().active());
    }

    @Test
    void driverWithoutUser_isAppliedWithNullUser() {
        listener.onEvent(message(EventTypes.DRIVER_UPDATED, payload(null, false)));

        verify(driverDirectoryService).applyDriverEvent(eventCaptor.capture());
        assertNull(eventCaptor.getValue().payload().userId());
        assertFalse(eventCaptor.getValue().payload().active());
    }

    @ParameterizedTest
    @ValueSource(strings = {"BusRegistered", "RouteCreated", "DriverDeleted"})
    void otherEvent_isIgnored(String eventType) {
        listener.onEvent(message(eventType, payload(USER_ID, true)));

        verifyNoInteractions(driverDirectoryService);
    }

    @Test
    void messageWithoutEventType_isIgnored() {
        listener.onEvent(jsonMapper.writeValueAsString(Map.of("eventId", EVENT_ID.toString())));

        verifyNoInteractions(driverDirectoryService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onEvent("{no es json"));

        verifyNoInteractions(driverDirectoryService);
    }

    private static Map<String, Object> payload(UUID userId, boolean active) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("driverId", DRIVER_ID.toString());
        payload.put("userId", userId == null ? null : userId.toString());
        payload.put("fullName", "Juan Perez");
        payload.put("licenseNumber", "LIC-001");
        payload.put("licenseType", "A");
        payload.put("licenseExpiresOn", EXPIRES_ON.toString());
        payload.put("phone", "55551234");
        payload.put("email", "juan@mail.com");
        payload.put("baseSalaryPerTrip", 150.00);
        payload.put("branchId", BRANCH_ID.toString());
        payload.put("active", active);
        return payload;
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", OCCURRED_AT.toString(),
                "aggregateId", DRIVER_ID.toString(),
                "version", 1,
                "payload", payload));
    }
}
