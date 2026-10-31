package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.domain.DriverDirectoryEntity;
import com.urbantransport.notification.domain.ProcessedEventEntity;
import com.urbantransport.notification.event.DriverEvent;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.repository.DriverDirectoryRepository;
import com.urbantransport.notification.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverDirectoryServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DRIVER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID BRANCH_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final LocalDate EXPIRES_ON = LocalDate.now(ZONE).plusDays(60);
    private static final Instant OCCURRED_AT = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Mock
    private DriverDirectoryRepository driverDirectoryRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Captor
    private ArgumentCaptor<DriverDirectoryEntity> driverCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEventEntity> processedEventCaptor;

    private DriverDirectoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DriverDirectoryServiceImpl(driverDirectoryRepository, processedEventRepository);
    }

    @Nested
    class ApplyDriverEvent {

        @Test
        void newDriver_isInsertedWithNormalizedEmailAndEventMarked() {
            when(driverDirectoryRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());

            service.applyDriverEvent(event(EventTypes.DRIVER_REGISTERED, OCCURRED_AT, payload(" Juan@Mail.COM ", true)));

            verify(driverDirectoryRepository).save(driverCaptor.capture());
            DriverDirectoryEntity saved = driverCaptor.getValue();
            assertEquals(DRIVER_ID, saved.getDriverId());
            assertEquals(USER_ID, saved.getUserId());
            assertEquals("Juan Perez", saved.getFullName());
            assertEquals("juan@mail.com", saved.getEmail());
            assertEquals(BRANCH_ID, saved.getBranchId());
            assertEquals("LIC-001", saved.getLicenseNumber());
            assertEquals(EXPIRES_ON, saved.getLicenseExpiresOn());
            assertTrue(saved.isActive());
            assertEquals(OCCURRED_AT, saved.getLastEventAt());
            assertMarkedProcessed();
        }

        @Test
        void newerEvent_overwritesExistingDriver() {
            DriverDirectoryEntity existing = driver(OCCURRED_AT.minusSeconds(60));
            when(driverDirectoryRepository.findById(DRIVER_ID)).thenReturn(Optional.of(existing));

            service.applyDriverEvent(event(EventTypes.DRIVER_UPDATED, OCCURRED_AT, payload("nuevo@mail.com", true)));

            verify(driverDirectoryRepository).save(driverCaptor.capture());
            assertSame(existing, driverCaptor.getValue());
            assertEquals("nuevo@mail.com", existing.getEmail());
            assertEquals(EXPIRES_ON, existing.getLicenseExpiresOn());
            assertEquals(OCCURRED_AT, existing.getLastEventAt());
            assertMarkedProcessed();
        }

        @ParameterizedTest
        @ValueSource(longs = {0, 60})
        void olderOrEqualEvent_isDiscardedButMarkedProcessed(long secondsBefore) {
            DriverDirectoryEntity existing = driver(OCCURRED_AT);
            when(driverDirectoryRepository.findById(DRIVER_ID)).thenReturn(Optional.of(existing));

            service.applyDriverEvent(event(EventTypes.DRIVER_UPDATED, OCCURRED_AT.minusSeconds(secondsBefore),
                    payload("viejo@mail.com", false)));

            verify(driverDirectoryRepository, never()).save(any());
            assertEquals("juan@mail.com", existing.getEmail());
            assertTrue(existing.isActive());
            assertMarkedProcessed();
        }

        @Test
        void driverDeactivated_isInactiveEvenIfPayloadSaysActive() {
            when(driverDirectoryRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());

            service.applyDriverEvent(event(EventTypes.DRIVER_DEACTIVATED, OCCURRED_AT, payload("juan@mail.com", true)));

            verify(driverDirectoryRepository).save(driverCaptor.capture());
            assertFalse(driverCaptor.getValue().isActive());
        }

        @Test
        void inactivePayload_isSavedInactive() {
            when(driverDirectoryRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());

            service.applyDriverEvent(event(EventTypes.DRIVER_UPDATED, OCCURRED_AT, payload("juan@mail.com", false)));

            verify(driverDirectoryRepository).save(driverCaptor.capture());
            assertFalse(driverCaptor.getValue().isActive());
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            service.applyDriverEvent(event(EventTypes.DRIVER_UPDATED, OCCURRED_AT, payload("juan@mail.com", true)));

            verifyNoInteractions(driverDirectoryRepository);
            verify(processedEventRepository, never()).save(any());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.DriverDirectoryServiceImplTest#incompleteEvents")
        void incompleteEvent_throwsAndSavesNothing(DriverEvent event) {
            assertThrows(IllegalArgumentException.class, () -> service.applyDriverEvent(event));

            verifyNoInteractions(driverDirectoryRepository);
            verify(processedEventRepository, never()).save(any());
        }
    }

    static Stream<DriverEvent> incompleteEvents() {
        return Stream.of(
                event(EventTypes.DRIVER_REGISTERED, OCCURRED_AT, null),
                event(EventTypes.DRIVER_REGISTERED, null, payload("juan@mail.com", true)),
                event(EventTypes.DRIVER_REGISTERED, OCCURRED_AT, new DriverEvent.Payload(null, USER_ID, "Juan",
                        "LIC-001", EXPIRES_ON, "juan@mail.com", BRANCH_ID, true)),
                event(EventTypes.DRIVER_REGISTERED, OCCURRED_AT, new DriverEvent.Payload(DRIVER_ID, USER_ID, null,
                        "LIC-001", EXPIRES_ON, "juan@mail.com", BRANCH_ID, true)),
                event(EventTypes.DRIVER_REGISTERED, OCCURRED_AT, new DriverEvent.Payload(DRIVER_ID, USER_ID, "Juan",
                        "LIC-001", EXPIRES_ON, null, BRANCH_ID, true)),
                event(EventTypes.DRIVER_REGISTERED, OCCURRED_AT, new DriverEvent.Payload(DRIVER_ID, USER_ID, "Juan",
                        "LIC-001", EXPIRES_ON, "juan@mail.com", null, true)),
                event(EventTypes.DRIVER_REGISTERED, OCCURRED_AT, new DriverEvent.Payload(DRIVER_ID, USER_ID, "Juan",
                        null, EXPIRES_ON, "juan@mail.com", BRANCH_ID, true)),
                event(EventTypes.DRIVER_REGISTERED, OCCURRED_AT, new DriverEvent.Payload(DRIVER_ID, USER_ID, "Juan",
                        "LIC-001", null, "juan@mail.com", BRANCH_ID, true)));
    }

    private void assertMarkedProcessed() {
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
    }

    private static DriverEvent event(String eventType, Instant occurredAt, DriverEvent.Payload payload) {
        return new DriverEvent(EVENT_ID, eventType, occurredAt, payload);
    }

    private static DriverEvent.Payload payload(String email, boolean active) {
        return new DriverEvent.Payload(DRIVER_ID, USER_ID, "Juan Perez", "LIC-001", EXPIRES_ON, email, BRANCH_ID,
                active);
    }

    private static DriverDirectoryEntity driver(Instant lastEventAt) {
        return DriverDirectoryEntity.builder()
                .driverId(DRIVER_ID)
                .userId(USER_ID)
                .fullName("Juan Perez")
                .email("juan@mail.com")
                .branchId(BRANCH_ID)
                .licenseNumber("LIC-001")
                .licenseExpiresOn(EXPIRES_ON.minusYears(1))
                .active(true)
                .lastEventAt(lastEventAt)
                .build();
    }
}
