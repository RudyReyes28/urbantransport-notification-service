package com.urbantransport.notification.event;

import com.urbantransport.notification.service.WalletNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentEventsListenerTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CUSTOMER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final LocalDate TODAY = LocalDate.now(ZONE);
    private static final Instant OCCURRED_AT = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock
    private WalletNotificationService walletNotificationService;

    @Captor
    private ArgumentCaptor<WalletRechargedEvent.Payload> payloadCaptor;

    private PaymentEventsListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentEventsListener(jsonMapper, walletNotificationService);
    }

    @ParameterizedTest
    @CsvSource({"100, 100", "250.75, 250.75"})
    void walletRecharged_isHandledWithAmountAsBigDecimal(String json, String expected) {
        listener.onEvent(message(EventTypes.WALLET_RECHARGED, payload(new BigDecimal(json), TODAY)));

        verify(walletNotificationService).handleRecharged(eq(EVENT_ID), eq(OCCURRED_AT), payloadCaptor.capture());
        assertEquals(CUSTOMER_ID, payloadCaptor.getValue().customerId());
        assertEquals(0, new BigDecimal(expected).compareTo(payloadCaptor.getValue().amount()));
        assertEquals(TODAY, payloadCaptor.getValue().paymentDate());
    }

    @Test
    void walletRechargedWithoutDate_isHandledWithNullDate() {
        listener.onEvent(message(EventTypes.WALLET_RECHARGED, payload(BigDecimal.TEN, null)));

        verify(walletNotificationService).handleRecharged(eq(EVENT_ID), eq(OCCURRED_AT), payloadCaptor.capture());
        assertNull(payloadCaptor.getValue().paymentDate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PaymentCompleted", "PaymentFailed", "RefundCompleted"})
    void otherEvent_isIgnored(String eventType) {
        listener.onEvent(message(eventType, payload(BigDecimal.TEN, TODAY)));

        verifyNoInteractions(walletNotificationService);
    }

    @Test
    void messageWithoutEventType_isIgnored() {
        listener.onEvent(jsonMapper.writeValueAsString(Map.of("eventId", EVENT_ID.toString())));

        verifyNoInteractions(walletNotificationService);
    }

    @Test
    void malformedMessage_throwsSoItGoesToRetryAndDlt() {
        assertThrows(JacksonException.class, () -> listener.onEvent("{no es json"));

        verifyNoInteractions(walletNotificationService);
    }

    private static Map<String, Object> payload(BigDecimal amount, LocalDate paymentDate) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerId", CUSTOMER_ID.toString());
        payload.put("amount", amount);
        payload.put("paymentDate", paymentDate == null ? null : paymentDate.toString());
        return payload;
    }

    private String message(String eventType, Map<String, Object> payload) {
        return jsonMapper.writeValueAsString(Map.of(
                "eventId", EVENT_ID.toString(),
                "eventType", eventType,
                "occurredAt", OCCURRED_AT.toString(),
                "aggregateId", CUSTOMER_ID.toString(),
                "version", 1,
                "payload", payload));
    }
}
