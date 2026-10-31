package com.urbantransport.notification.event;

import com.urbantransport.notification.service.UserDirectoryService;
import com.urbantransport.notification.service.WelcomeNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class IdentityEventsListenerTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BRANCH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant OCCURRED_AT = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private UserDirectoryService userDirectoryService;

    @Mock
    private WelcomeNotificationService welcomeNotificationService;

    @Captor
    private ArgumentCaptor<UserEvent> eventCaptor;

    private IdentityEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new IdentityEventsListener(jsonMapper, userDirectoryService, welcomeNotificationService);
    }

    @Test
    void userCreated_isAppliedToDirectoryAndThenWelcomed() {
        listener.onEvent(message(EventTypes.USER_CREATED, payload("CASHIER", BRANCH_ID, true)));

        InOrder order = inOrder(userDirectoryService, welcomeNotificationService);
        order.verify(userDirectoryService).applyUserEvent(eventCaptor.capture());
        order.verify(welcomeNotificationService).handleUserCreated(eventCaptor.capture());
        UserEvent event = eventCaptor.getAllValues().getFirst();
        assertSame(event, eventCaptor.getAllValues().getLast());
        assertEquals(EVENT_ID, event.eventId());
        assertEquals(EventTypes.USER_CREATED, event.eventType());
        assertEquals(OCCURRED_AT, event.occurredAt());
        assertEquals(USER_ID, event.payload().userId());
        assertEquals("Ana@Mail.com", event.payload().email());
        assertEquals("Ana Lopez", event.payload().fullName());
        assertEquals("CASHIER", event.payload().role());
        assertEquals(BRANCH_ID, event.payload().branchId());
        assertNull(event.payload().customerId());
        assertTrue(event.payload().active());
    }

    @ParameterizedTest
    @ValueSource(strings = {EventTypes.USER_UPDATED, EventTypes.USER_DEACTIVATED})
    void userUpdatedOrDeactivated_isAppliedWithoutWelcome(String eventType) {
        listener.onEvent(message(eventType, payload("CUSTOMER", null, false)));

        verify(userDirectoryService).applyUserEvent(eventCaptor.capture());
        assertEquals(eventType, eventCaptor.getValue().eventType());
        assertNull(eventCaptor.getValue().payload().branchId());
        assertFalse(eventCaptor.getValue().payload().active());
        verifyNoInteractions(welcomeNotificationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BranchCreated", "BranchUpdated", ""})
    void otherEvent_isIgnored(String eventType) {
        listener.onEvent(message(eventType, payload("CASHIER", BRANCH_ID, true)));

        verifyNoInteractions(userDirectoryService, welcomeNotificationService);
    }

    @Test
    void messageWithoutEventType_isIgnored() {
        listener.onEvent(jsonMapper.writeValueAsString(Map.of("eventId", EVENT_ID.toString(), "payload", Map.of())));

        verifyNoInteractions(userDirectoryService, welcomeNotificationService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onEvent("{no es json"));

        verifyNoInteractions(userDirectoryService, welcomeNotificationService);
    }

    private static Map<String, Object> payload(String role, UUID branchId, boolean active) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", USER_ID.toString());
        payload.put("email", "Ana@Mail.com");
        payload.put("fullName", "Ana Lopez");
        payload.put("role", role);
        payload.put("branchId", branchId == null ? null : branchId.toString());
        payload.put("customerId", null);
        payload.put("active", active);
        payload.put("nit", "1234567");
        return payload;
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", OCCURRED_AT.toString(),
                "aggregateId", USER_ID.toString(),
                "version", 1,
                "payload", payload));
    }
}
