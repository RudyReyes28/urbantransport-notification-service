package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.domain.ProcessedEventEntity;
import com.urbantransport.notification.domain.Role;
import com.urbantransport.notification.domain.UserDirectoryEntity;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.event.UserEvent;
import com.urbantransport.notification.repository.ProcessedEventRepository;
import com.urbantransport.notification.repository.UserDirectoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDirectoryServiceImplTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BRANCH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CUSTOMER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant OCCURRED_AT = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Mock
    private UserDirectoryRepository userDirectoryRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Captor
    private ArgumentCaptor<UserDirectoryEntity> userCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEventEntity> processedEventCaptor;

    private UserDirectoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserDirectoryServiceImpl(userDirectoryRepository, processedEventRepository);
    }

    @Nested
    class ApplyUserEvent {

        @Test
        void newStaffUser_isInsertedWithNormalizedEmailAndEventMarked() {
            when(userDirectoryRepository.findById(USER_ID)).thenReturn(Optional.empty());

            service.applyUserEvent(event(EventTypes.USER_CREATED, OCCURRED_AT,
                    payload(" Ana@Mail.COM ", "BRANCH_ADMIN", BRANCH_ID, null, true)));

            verify(userDirectoryRepository).save(userCaptor.capture());
            UserDirectoryEntity saved = userCaptor.getValue();
            assertEquals(USER_ID, saved.getUserId());
            assertEquals("ana@mail.com", saved.getEmail());
            assertEquals("Ana Lopez", saved.getFullName());
            assertEquals(Role.BRANCH_ADMIN, saved.getRole());
            assertEquals(BRANCH_ID, saved.getBranchId());
            assertNull(saved.getCustomerId());
            assertTrue(saved.isActive());
            assertEquals(OCCURRED_AT, saved.getLastEventAt());
            assertMarkedProcessed();
        }

        @Test
        void newCustomer_isInsertedWithCustomerId() {
            when(userDirectoryRepository.findById(USER_ID)).thenReturn(Optional.empty());

            service.applyUserEvent(event(EventTypes.USER_CREATED, OCCURRED_AT,
                    payload("ana@mail.com", "CUSTOMER", null, CUSTOMER_ID, true)));

            verify(userDirectoryRepository).save(userCaptor.capture());
            assertEquals(Role.CUSTOMER, userCaptor.getValue().getRole());
            assertEquals(CUSTOMER_ID, userCaptor.getValue().getCustomerId());
            assertNull(userCaptor.getValue().getBranchId());
        }

        @ParameterizedTest
        @EnumSource(Role.class)
        void everyKnownRole_entersTheDirectory(Role role) {
            when(userDirectoryRepository.findById(USER_ID)).thenReturn(Optional.empty());

            service.applyUserEvent(event(EventTypes.USER_CREATED, OCCURRED_AT,
                    payload("ana@mail.com", role.name(), null, null, true)));

            verify(userDirectoryRepository).save(userCaptor.capture());
            assertEquals(role, userCaptor.getValue().getRole());
        }

        @Test
        void newerEvent_overwritesExistingUser() {
            UserDirectoryEntity existing = user(OCCURRED_AT.minusSeconds(60));
            when(userDirectoryRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

            service.applyUserEvent(event(EventTypes.USER_UPDATED, OCCURRED_AT,
                    payload("nuevo@mail.com", "CASHIER", BRANCH_ID, null, true)));

            verify(userDirectoryRepository).save(userCaptor.capture());
            assertSame(existing, userCaptor.getValue());
            assertEquals("nuevo@mail.com", existing.getEmail());
            assertEquals(Role.CASHIER, existing.getRole());
            assertEquals(OCCURRED_AT, existing.getLastEventAt());
            assertMarkedProcessed();
        }

        @ParameterizedTest
        @ValueSource(longs = {0, 60})
        void olderOrEqualEvent_isDiscardedButMarkedProcessed(long secondsBefore) {
            UserDirectoryEntity existing = user(OCCURRED_AT);
            when(userDirectoryRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

            service.applyUserEvent(event(EventTypes.USER_DEACTIVATED, OCCURRED_AT.minusSeconds(secondsBefore),
                    payload("viejo@mail.com", "CASHIER", BRANCH_ID, null, false)));

            verify(userDirectoryRepository, never()).save(any());
            assertTrue(existing.isActive());
            assertEquals("ana@mail.com", existing.getEmail());
            assertMarkedProcessed();
        }

        @Test
        void userDeactivated_isInactiveEvenIfPayloadSaysActive() {
            when(userDirectoryRepository.findById(USER_ID)).thenReturn(Optional.empty());

            service.applyUserEvent(event(EventTypes.USER_DEACTIVATED, OCCURRED_AT,
                    payload("ana@mail.com", "CASHIER", BRANCH_ID, null, true)));

            verify(userDirectoryRepository).save(userCaptor.capture());
            assertFalse(userCaptor.getValue().isActive());
        }

        @Test
        void inactivePayload_isSavedInactive() {
            when(userDirectoryRepository.findById(USER_ID)).thenReturn(Optional.empty());

            service.applyUserEvent(event(EventTypes.USER_UPDATED, OCCURRED_AT,
                    payload("ana@mail.com", "CASHIER", BRANCH_ID, null, false)));

            verify(userDirectoryRepository).save(userCaptor.capture());
            assertFalse(userCaptor.getValue().isActive());
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"AUDITOR", "branch_admin", ""})
        void unknownRole_isNotSavedButEventIsMarked(String role) {
            service.applyUserEvent(event(EventTypes.USER_CREATED, OCCURRED_AT,
                    payload("ana@mail.com", role, BRANCH_ID, null, true)));

            verifyNoInteractions(userDirectoryRepository);
            assertMarkedProcessed();
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            service.applyUserEvent(event(EventTypes.USER_UPDATED, OCCURRED_AT,
                    payload("ana@mail.com", "CASHIER", BRANCH_ID, null, true)));

            verifyNoInteractions(userDirectoryRepository);
            verify(processedEventRepository, never()).save(any());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.UserDirectoryServiceImplTest#incompleteEvents")
        void incompleteEvent_throwsAndSavesNothing(UserEvent event) {
            assertThrows(IllegalArgumentException.class, () -> service.applyUserEvent(event));

            verifyNoInteractions(userDirectoryRepository);
            verify(processedEventRepository, never()).save(any());
        }
    }

    static Stream<UserEvent> incompleteEvents() {
        return Stream.of(
                event(EventTypes.USER_CREATED, OCCURRED_AT, null),
                event(EventTypes.USER_CREATED, null, payload("ana@mail.com", "CASHIER", BRANCH_ID, null, true)),
                event(EventTypes.USER_CREATED, OCCURRED_AT, new UserEvent.UserPayload(null, "ana@mail.com", "Ana",
                        "CASHIER", BRANCH_ID, null, true)),
                event(EventTypes.USER_CREATED, OCCURRED_AT, payload(null, "CASHIER", BRANCH_ID, null, true)),
                event(EventTypes.USER_CREATED, OCCURRED_AT, new UserEvent.UserPayload(USER_ID, "ana@mail.com", null,
                        "CASHIER", BRANCH_ID, null, true)));
    }

    private void assertMarkedProcessed() {
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
    }

    private static UserEvent event(String eventType, Instant occurredAt, UserEvent.UserPayload payload) {
        return new UserEvent(EVENT_ID, eventType, occurredAt, payload);
    }

    private static UserEvent.UserPayload payload(String email, String role, UUID branchId, UUID customerId,
                                                 boolean active) {
        return new UserEvent.UserPayload(USER_ID, email, "Ana Lopez", role, branchId, customerId, active);
    }

    private static UserDirectoryEntity user(Instant lastEventAt) {
        return UserDirectoryEntity.builder()
                .userId(USER_ID)
                .email("ana@mail.com")
                .fullName("Ana Lopez")
                .role(Role.BRANCH_ADMIN)
                .branchId(BRANCH_ID)
                .active(true)
                .lastEventAt(lastEventAt)
                .build();
    }
}
