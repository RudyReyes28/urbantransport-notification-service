package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationEventProperties;
import com.urbantransport.notification.domain.DriverDirectoryEntity;
import com.urbantransport.notification.domain.PrivateTripLegEntity;
import com.urbantransport.notification.domain.ProcessedEventEntity;
import com.urbantransport.notification.domain.TripDirectoryEntity;
import com.urbantransport.notification.domain.TripKind;
import com.urbantransport.notification.domain.TripPassengerEntity;
import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.Tone;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.event.PrivateTripRejectedEvent;
import com.urbantransport.notification.event.TripDeletedEvent;
import com.urbantransport.notification.event.TripUpsertEvent;
import com.urbantransport.notification.repository.DriverDirectoryRepository;
import com.urbantransport.notification.repository.PrivateTripLegRepository;
import com.urbantransport.notification.repository.ProcessedEventRepository;
import com.urbantransport.notification.repository.TripDirectoryRepository;
import com.urbantransport.notification.repository.TripPassengerRepository;
import com.urbantransport.notification.service.EventMailer;
import com.urbantransport.notification.service.MailRequest;
import com.urbantransport.notification.service.PrivateTripService;
import com.urbantransport.notification.service.RecipientResolver;
import com.urbantransport.notification.util.EmailFormats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class TripNotificationServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant RECENT = NOW.minusSeconds(60);
    private static final Instant STALE = NOW.minus(3, ChronoUnit.DAYS);
    private static final LocalDateTime DEPARTURE = LocalDate.now(ZONE).plusDays(4).atTime(6, 0);
    private static final LocalDateTime ARRIVAL = DEPARTURE.plusHours(4);
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRIP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BUS_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DRIVER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID OTHER_DRIVER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID RENTAL_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID BRANCH_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID CUSTOMER_USER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final String NO_DRIVER = "El chofer no está en el directorio";
    private static final NotificationEventProperties PROPERTIES = new NotificationEventProperties(Duration.ofHours(24),
            Duration.ofDays(7), Duration.ofDays(2), Duration.ofDays(2), Duration.ofDays(2), Duration.ofDays(2));

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private TripDirectoryRepository tripRepository;

    @Mock
    private TripPassengerRepository passengerRepository;

    @Mock
    private PrivateTripLegRepository legRepository;

    @Mock
    private DriverDirectoryRepository driverRepository;

    @Mock
    private PrivateTripService privateTripService;

    @Mock
    private RecipientResolver recipientResolver;

    @Mock
    private EventMailer eventMailer;

    @Captor
    private ArgumentCaptor<TripDirectoryEntity> tripCaptor;

    @Captor
    private ArgumentCaptor<MailRequest> mailCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEventEntity> processedEventCaptor;

    private TripNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TripNotificationServiceImpl(processedEventRepository, tripRepository, passengerRepository,
                legRepository, driverRepository, privateTripService, recipientResolver, eventMailer, PROPERTIES,
                CLOCK);
    }

    @Nested
    class HandleTripUpsert {

        @Test
        void scheduledRegularTrip_isStoredAndDriverIsNotified() {
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
            when(tripRepository.save(any(TripDirectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(DRIVER_ID, "Juan Perez")));

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_SCHEDULED, RECENT,
                    regular(DEPARTURE.plusSeconds(30), ARRIVAL, "P-001", DRIVER_ID));

            verify(tripRepository).save(tripCaptor.capture());
            TripDirectoryEntity saved = tripCaptor.getValue();
            assertEquals(TRIP_ID, saved.getTripId());
            assertEquals(TripKind.REGULAR, saved.getType());
            assertEquals("Central", saved.getOriginName());
            assertEquals("Xela", saved.getDestinationName());
            assertEquals(DEPARTURE, saved.getDepartureAt());
            assertEquals(ARRIVAL, saved.getEstimatedArrival());
            assertEquals(BUS_ID, saved.getBusId());
            assertEquals("P-001", saved.getBusPlate());
            assertEquals(DRIVER_ID, saved.getDriverId());
            assertEquals("SCHEDULED", saved.getStatus());
            assertEquals(RECENT, saved.getLastEventAt());
            verify(eventMailer).send(mailCaptor.capture());
            MailRequest mail = mailCaptor.getValue();
            assertEquals(EVENT_ID, mail.eventId());
            assertEquals(EventTypes.TRIP_SCHEDULED, mail.eventType());
            assertEquals(TRIP_ID, mail.referenceId());
            assertEquals("trip-driver-assigned", mail.template());
            assertEquals("Nuevo viaje asignado: Central → Xela, " + EmailFormats.shortDateTime(DEPARTURE),
                    mail.subject());
            assertEquals(List.of("juan@mail.com"), mail.recipients());
            assertEquals(NO_DRIVER, mail.skipReason());
            assertEquals("Hola Juan Perez, se te asignó un nuevo viaje.", mail.view().getIntro());
            assertEquals(EmailFormats.longDateTime(DEPARTURE), rowValue(mail, "Salida"));
            assertEquals("P-001", rowValue(mail, "Bus"));
            verifyNoInteractions(privateTripService);
            assertMarkedProcessed();
        }

        @Test
        void scheduledTripWithUnknownDriverAndNoPlate_isSentWithoutRecipients() {
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
            when(tripRepository.save(any(TripDirectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_SCHEDULED, RECENT,
                    regular(DEPARTURE, ARRIVAL, " ", DRIVER_ID));

            verify(eventMailer).send(mailCaptor.capture());
            assertTrue(mailCaptor.getValue().recipients().isEmpty());
            assertEquals("Hola, se te asignó un nuevo viaje.", mailCaptor.getValue().view().getIntro());
            assertEquals("—", rowValue(mailCaptor.getValue(), "Bus"));
        }

        @Test
        void scheduledTripWithoutDriver_doesNotLookItUp() {
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
            when(tripRepository.save(any(TripDirectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_SCHEDULED, RECENT,
                    regular(DEPARTURE, ARRIVAL, "P-001", null));

            verifyNoInteractions(driverRepository);
            verify(eventMailer).send(mailCaptor.capture());
            assertTrue(mailCaptor.getValue().recipients().isEmpty());
        }

        @Test
        void staleScheduledTrip_isStoredButMailIsSkipped() {
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
            when(tripRepository.save(any(TripDirectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_SCHEDULED, STALE,
                    regular(DEPARTURE, ARRIVAL, "P-001", DRIVER_ID));

            verify(tripRepository).save(any(TripDirectoryEntity.class));
            verify(eventMailer).send(mailCaptor.capture());
            assertSkipped(mailCaptor.getValue(), "trip-driver-assigned");
            verifyNoInteractions(driverRepository);
        }

        @Test
        void privateTripWithTextsInsteadOfNames_isStoredAndLinked() {
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
            when(tripRepository.save(any(TripDirectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_SCHEDULED, RECENT, privatePayload());

            verify(tripRepository).save(tripCaptor.capture());
            assertEquals(TripKind.PRIVATE, tripCaptor.getValue().getType());
            assertEquals("Hotel Antigua", tripCaptor.getValue().getOriginName());
            assertEquals("Lago Atitlan", tripCaptor.getValue().getDestinationName());
            verify(privateTripService).linkScheduledTrip(RECENT, tripCaptor.getValue());
            verifyNoInteractions(eventMailer);
            assertMarkedProcessed();
        }

        @Test
        void privateTripAlreadyLinked_isNotLinkedAgain() {
            TripDirectoryEntity current = trip(TripKind.PRIVATE, "P-001", DRIVER_ID, RECENT.minusSeconds(60));
            current.setRentalId(RENTAL_ID);
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(current));
            when(tripRepository.save(any(TripDirectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_SCHEDULED, RECENT, privatePayload());

            assertEquals(RENTAL_ID, current.getRentalId());
            verifyNoInteractions(privateTripService, eventMailer);
        }

        @Test
        void updatedPrivateTrip_sendsNothing() {
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
            when(tripRepository.save(any(TripDirectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_UPDATED, RECENT, privatePayload());

            verifyNoInteractions(privateTripService, eventMailer);
        }

        @Test
        void updatedTripWithoutPreviousState_sendsNothing() {
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
            when(tripRepository.save(any(TripDirectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_UPDATED, RECENT,
                    regular(DEPARTURE, ARRIVAL, "P-001", DRIVER_ID));

            verify(tripRepository).save(any(TripDirectoryEntity.class));
            verifyNoInteractions(eventMailer, passengerRepository);
            assertMarkedProcessed();
        }

        @Test
        void updatedTripWithoutRelevantChanges_sendsNothing() {
            TripDirectoryEntity current = trip(TripKind.REGULAR, "P-001", DRIVER_ID, RECENT.minusSeconds(60));
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(current));
            when(tripRepository.save(any(TripDirectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_UPDATED, RECENT,
                    regular(DEPARTURE, ARRIVAL, "P-001", DRIVER_ID));

            verifyNoInteractions(eventMailer, passengerRepository);
        }

        @Test
        void changedSchedule_notifiesEachBuyerOnceAndTheDriver() {
            TripDirectoryEntity current = trip(TripKind.REGULAR, "P-001", DRIVER_ID, RECENT.minusSeconds(60));
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(current));
            when(tripRepository.save(any(TripDirectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(passengerRepository.findByTripId(TRIP_ID)).thenReturn(List.of(passenger("ana@mail.com", "1A"),
                    passenger("luis@mail.com", "3C"), passenger("ana@mail.com", "1B")));
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(DRIVER_ID, "Juan Perez")));

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_UPDATED, RECENT,
                    regular(DEPARTURE.plusHours(1), ARRIVAL.plusHours(1), "P-001", DRIVER_ID));

            verify(eventMailer, times(3)).send(mailCaptor.capture());
            List<MailRequest> mails = mailCaptor.getAllValues();
            MailRequest ana = mails.get(0);
            assertEquals("trip-updated-passenger", ana.template());
            assertEquals("Cambió tu viaje: Central → Xela", ana.subject());
            assertEquals(List.of("ana@mail.com"), ana.recipients());
            assertNull(ana.skipReason());
            assertEquals("1A, 1B", rowValue(ana, "Tus asientos"));
            assertEquals(EmailFormats.shortDateTime(DEPARTURE) + " → "
                    + EmailFormats.shortDateTime(DEPARTURE.plusHours(1)), rowValue(ana, "Salida"));
            assertEquals(EmailFormats.shortDateTime(ARRIVAL) + " → "
                    + EmailFormats.shortDateTime(ARRIVAL.plusHours(1)), rowValue(ana, "Llegada estimada"));
            assertNull(rowValue(ana, "Bus"));
            assertEquals(List.of("luis@mail.com"), mails.get(1).recipients());
            assertEquals("3C", rowValue(mails.get(1), "Tus asientos"));
            MailRequest driverMail = mails.get(2);
            assertEquals("trip-updated-driver", driverMail.template());
            assertEquals(Tone.WARNING, driverMail.view().getTone());
            assertEquals(List.of("juan@mail.com"), driverMail.recipients());
            assertNull(rowValue(driverMail, "Tus asientos"));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = "P-001")
        void changedBus_showsOldAndNewPlate(String oldPlate) {
            TripDirectoryEntity current = trip(TripKind.REGULAR, oldPlate, DRIVER_ID, RECENT.minusSeconds(60));
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(current));
            when(tripRepository.save(any(TripDirectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(passengerRepository.findByTripId(TRIP_ID)).thenReturn(List.of());
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_UPDATED, RECENT,
                    regular(DEPARTURE, ARRIVAL, "P-002", DRIVER_ID));

            verify(eventMailer).send(mailCaptor.capture());
            MailRequest driverMail = mailCaptor.getValue();
            assertEquals((oldPlate == null ? "—" : oldPlate) + " → P-002", rowValue(driverMail, "Bus"));
            assertTrue(driverMail.recipients().isEmpty());
            assertEquals("Hola, se modificó un viaje que tienes asignado.", driverMail.view().getIntro());
        }

        @Test
        void changedDriverOnly_notifiesNewAndOldDriverButNotPassengers() {
            TripDirectoryEntity current = trip(TripKind.REGULAR, "P-001", DRIVER_ID, RECENT.minusSeconds(60));
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(current));
            when(tripRepository.save(any(TripDirectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(driverRepository.findById(OTHER_DRIVER_ID))
                    .thenReturn(Optional.of(driver(OTHER_DRIVER_ID, "Pedro Gomez")));
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(DRIVER_ID, "Juan Perez")));

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_UPDATED, RECENT,
                    regular(DEPARTURE, ARRIVAL, "P-001", OTHER_DRIVER_ID));

            verifyNoInteractions(passengerRepository);
            verify(eventMailer, times(2)).send(mailCaptor.capture());
            MailRequest assigned = mailCaptor.getAllValues().getFirst();
            assertEquals("trip-driver-assigned", assigned.template());
            assertEquals(List.of("pedro@mail.com"), assigned.recipients());
            assertEquals("Hola Pedro Gomez, se te asignó un nuevo viaje.", assigned.view().getIntro());
            MailRequest unassigned = mailCaptor.getAllValues().getLast();
            assertEquals("trip-driver-unassigned", unassigned.template());
            assertEquals("Ya no estás asignado al viaje Central → Xela", unassigned.subject());
            assertEquals(List.of("juan@mail.com"), unassigned.recipients());
            assertEquals("Hola Juan Perez, la sucursal asignó otro chofer a este viaje.",
                    unassigned.view().getIntro());
            assertNull(unassigned.view().getNote());
        }

        @Test
        void changedDriverAndSchedule_notifiesPassengersAndBothDrivers() {
            TripDirectoryEntity current = trip(TripKind.REGULAR, "P-001", DRIVER_ID, RECENT.minusSeconds(60));
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(current));
            when(tripRepository.save(any(TripDirectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(passengerRepository.findByTripId(TRIP_ID)).thenReturn(List.of(passenger("ana@mail.com", "1A")));
            when(driverRepository.findById(OTHER_DRIVER_ID)).thenReturn(Optional.empty());
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_UPDATED, RECENT,
                    regular(DEPARTURE.plusHours(2), ARRIVAL.plusHours(2), "P-001", OTHER_DRIVER_ID));

            verify(eventMailer, times(3)).send(mailCaptor.capture());
            assertEquals(List.of("trip-updated-passenger", "trip-driver-assigned", "trip-driver-unassigned"),
                    mailCaptor.getAllValues().stream().map(MailRequest::template).toList());
            assertEquals("Hola, la sucursal asignó otro chofer a este viaje.",
                    mailCaptor.getAllValues().getLast().view().getIntro());
        }

        @Test
        void staleUpdate_isSkippedWithPassengerTemplate() {
            TripDirectoryEntity current = trip(TripKind.REGULAR, "P-001", DRIVER_ID, STALE.minusSeconds(60));
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(current));
            when(tripRepository.save(any(TripDirectoryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_UPDATED, STALE,
                    regular(DEPARTURE.plusHours(1), ARRIVAL, "P-001", DRIVER_ID));

            verify(eventMailer).send(mailCaptor.capture());
            assertSkipped(mailCaptor.getValue(), "trip-updated-passenger");
            verifyNoInteractions(passengerRepository, driverRepository);
        }

        @ParameterizedTest
        @ValueSource(longs = {0, 60})
        void olderOrEqualEvent_isDiscardedButMarkedProcessed(long secondsBefore) {
            TripDirectoryEntity current = trip(TripKind.REGULAR, "P-001", DRIVER_ID, RECENT);
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(current));

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_UPDATED, RECENT.minusSeconds(secondsBefore),
                    regular(DEPARTURE.plusHours(5), ARRIVAL, "P-009", OTHER_DRIVER_ID));

            verify(tripRepository, never()).save(any());
            assertEquals("P-001", current.getBusPlate());
            verifyNoInteractions(eventMailer, privateTripService);
            assertMarkedProcessed();
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"BUS", "regular", ""})
        void invalidTripType_throwsAndSavesNothing(String type) {
            TripUpsertEvent.Payload payload = new TripUpsertEvent.Payload(TRIP_ID, type, "Central", "Xela", null, null,
                    BUS_ID, "P-001", DRIVER_ID, DEPARTURE, ARRIVAL, "SCHEDULED");

            assertThrows(IllegalArgumentException.class,
                    () -> service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_SCHEDULED, RECENT, payload));

            verifyNoInteractions(tripRepository, eventMailer);
            verify(processedEventRepository, never()).save(any());
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_SCHEDULED, RECENT,
                    regular(DEPARTURE, ARRIVAL, "P-001", DRIVER_ID));

            verifyNoInteractions(tripRepository, eventMailer, privateTripService);
            verify(processedEventRepository, never()).save(any());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.TripNotificationServiceImplTest#incompleteUpserts")
        void incompleteEvent_throwsAndSavesNothing(Instant occurredAt, TripUpsertEvent.Payload payload) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.handleTripUpsert(EVENT_ID, EventTypes.TRIP_SCHEDULED, occurredAt, payload));

            verifyNoInteractions(tripRepository, eventMailer);
            verify(processedEventRepository, never()).save(any());
        }
    }

    @Nested
    class HandleTripDeleted {

        @Test
        void knownRegularTrip_isMarkedDeletedAndDriverIsNotified() {
            TripDirectoryEntity current = trip(TripKind.REGULAR, "P-001", DRIVER_ID, RECENT.minusSeconds(60));
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(current));
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(DRIVER_ID, "Juan Perez")));

            service.handleTripDeleted(EVENT_ID, RECENT, new TripDeletedEvent.Payload(TRIP_ID, "REGULAR", null,
                    DRIVER_ID));

            assertEquals("DELETED", current.getStatus());
            verify(tripRepository).save(current);
            verify(eventMailer).send(mailCaptor.capture());
            MailRequest mail = mailCaptor.getValue();
            assertEquals(EventTypes.TRIP_DELETED, mail.eventType());
            assertEquals("trip-deleted-driver", mail.template());
            assertEquals("Viaje cancelado: Central → Xela, " + EmailFormats.shortDateTime(DEPARTURE), mail.subject());
            assertEquals(Tone.DANGER, mail.view().getTone());
            assertEquals(List.of("juan@mail.com"), mail.recipients());
            assertEquals("Central → Xela", rowValue(mail, "Viaje"));
            verifyNoInteractions(legRepository, recipientResolver);
            assertMarkedProcessed();
        }

        @Test
        void payloadWithoutDriver_usesTheStoredDriver() {
            TripDirectoryEntity current = trip(TripKind.REGULAR, "P-001", DRIVER_ID, RECENT.minusSeconds(60));
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(current));
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(DRIVER_ID, "Juan Perez")));

            service.handleTripDeleted(EVENT_ID, RECENT, new TripDeletedEvent.Payload(TRIP_ID, "REGULAR", null, null));

            verify(eventMailer).send(mailCaptor.capture());
            assertEquals(List.of("juan@mail.com"), mailCaptor.getValue().recipients());
        }

        @Test
        void unknownTripWithoutDriver_sendsGenericMailWithoutRecipients() {
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());

            service.handleTripDeleted(EVENT_ID, RECENT, new TripDeletedEvent.Payload(TRIP_ID, "REGULAR", null, null));

            verify(tripRepository, never()).save(any());
            verify(eventMailer).send(mailCaptor.capture());
            assertEquals("Viaje cancelado", mailCaptor.getValue().subject());
            assertTrue(mailCaptor.getValue().view().getRows().isEmpty());
            assertTrue(mailCaptor.getValue().recipients().isEmpty());
            verifyNoInteractions(driverRepository);
        }

        @Test
        void deletedPrivateTrip_alsoNotifiesTheCustomer() {
            TripDirectoryEntity current = trip(TripKind.PRIVATE, "P-001", DRIVER_ID, RECENT.minusSeconds(60));
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(current));
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());
            when(legRepository.findByRentalIdOrderByLegNoAsc(RENTAL_ID)).thenReturn(List.of(leg()));
            when(recipientResolver.userEmail(CUSTOMER_USER_ID)).thenReturn(List.of("cliente@mail.com"));

            service.handleTripDeleted(EVENT_ID, RECENT, new TripDeletedEvent.Payload(TRIP_ID, "PRIVATE", RENTAL_ID,
                    DRIVER_ID));

            verify(eventMailer, times(2)).send(mailCaptor.capture());
            MailRequest customer = mailCaptor.getAllValues().getLast();
            assertEquals("trip-deleted-customer", customer.template());
            assertEquals("Tu viaje privado fue cancelado", customer.subject());
            assertEquals(List.of("cliente@mail.com"), customer.recipients());
            assertEquals("El alquiler no tiene un cliente con correo en el directorio", customer.skipReason());
            assertEquals("Hola, la sucursal canceló un viaje que tenías asignado.",
                    mailCaptor.getAllValues().getFirst().view().getIntro());
        }

        @Test
        void deletedPrivateTripWithoutLegs_hasNoCustomer() {
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.empty());
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());
            when(legRepository.findByRentalIdOrderByLegNoAsc(RENTAL_ID)).thenReturn(List.of());
            when(recipientResolver.userEmail(null)).thenReturn(List.of());

            service.handleTripDeleted(EVENT_ID, RECENT, new TripDeletedEvent.Payload(TRIP_ID, "PRIVATE", RENTAL_ID,
                    DRIVER_ID));

            verify(eventMailer, times(2)).send(mailCaptor.capture());
            assertTrue(mailCaptor.getAllValues().getLast().recipients().isEmpty());
        }

        @Test
        void staleDeletion_isSkippedButStatusIsUpdated() {
            TripDirectoryEntity current = trip(TripKind.PRIVATE, "P-001", DRIVER_ID, RECENT.minusSeconds(60));
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(current));

            service.handleTripDeleted(EVENT_ID, STALE, new TripDeletedEvent.Payload(TRIP_ID, "PRIVATE", RENTAL_ID,
                    DRIVER_ID));

            assertEquals("DELETED", current.getStatus());
            verify(eventMailer).send(mailCaptor.capture());
            assertSkipped(mailCaptor.getValue(), "trip-deleted-driver");
            verifyNoInteractions(driverRepository, legRepository, recipientResolver);
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            service.handleTripDeleted(EVENT_ID, RECENT, new TripDeletedEvent.Payload(TRIP_ID, "REGULAR", null, null));

            verifyNoInteractions(tripRepository, eventMailer);
            verify(processedEventRepository, never()).save(any());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.TripNotificationServiceImplTest#incompleteDeletions")
        void incompleteEvent_throwsAndSavesNothing(Instant occurredAt, TripDeletedEvent.Payload payload) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.handleTripDeleted(EVENT_ID, occurredAt, payload));

            verifyNoInteractions(tripRepository, eventMailer);
            verify(processedEventRepository, never()).save(any());
        }
    }

    @Nested
    class HandlePrivateTripRejected {

        @Test
        void rejection_notifiesBranchAdminsAndDiscardsUnlinkedLegs() {
            when(legRepository.findByRentalIdOrderByLegNoAsc(RENTAL_ID)).thenReturn(List.of(leg()));
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver(DRIVER_ID, "Juan Perez")));
            when(recipientResolver.branchAdmins(BRANCH_ID)).thenReturn(List.of("admin@mail.com"));

            service.handlePrivateTripRejected(EVENT_ID, RECENT, rejection("El bus ya tiene un viaje"));

            verify(eventMailer).send(mailCaptor.capture());
            MailRequest mail = mailCaptor.getValue();
            assertEquals(EventTypes.PRIVATE_TRIP_REJECTED, mail.eventType());
            assertEquals(RENTAL_ID, mail.referenceId());
            assertEquals("private-trip-rejected-admin", mail.template());
            assertEquals("Viaje privado rechazado: Hotel Antigua → Lago Atitlan", mail.subject());
            assertEquals(List.of("admin@mail.com"), mail.recipients());
            assertEquals("No hay administradores activos en el directorio", mail.skipReason());
            assertEquals("El bus ya tiene un viaje", rowValue(mail, "Motivo"));
            assertEquals(EmailFormats.shortDateTime(DEPARTURE), rowValue(mail, "Salida"));
            assertEquals("Juan Perez", rowValue(mail, "Chofer"));
            verify(legRepository).deleteUnlinkedByRentalId(RENTAL_ID);
            assertMarkedProcessed();
        }

        @Test
        void rejectionWithoutReasonAndUnknownDriver_showsDashes() {
            when(legRepository.findByRentalIdOrderByLegNoAsc(RENTAL_ID)).thenReturn(List.of(leg()));
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());
            when(recipientResolver.branchAdmins(BRANCH_ID)).thenReturn(List.of());

            service.handlePrivateTripRejected(EVENT_ID, RECENT, rejection(null));

            verify(eventMailer).send(mailCaptor.capture());
            assertEquals("—", rowValue(mailCaptor.getValue(), "Motivo"));
            assertEquals("—", rowValue(mailCaptor.getValue(), "Chofer"));
        }

        @Test
        void staleRejection_isSkippedButLegsAreDiscarded() {
            when(legRepository.findByRentalIdOrderByLegNoAsc(RENTAL_ID)).thenReturn(List.of(leg()));

            service.handlePrivateTripRejected(EVENT_ID, STALE, rejection("x"));

            verify(eventMailer).send(mailCaptor.capture());
            assertSkipped(mailCaptor.getValue(), "private-trip-rejected-admin");
            verify(legRepository).deleteUnlinkedByRentalId(RENTAL_ID);
            verifyNoInteractions(recipientResolver, driverRepository);
            assertMarkedProcessed();
        }

        @Test
        void rejectionBeforeAssignment_throwsSoItIsRetried() {
            when(legRepository.findByRentalIdOrderByLegNoAsc(RENTAL_ID)).thenReturn(List.of());

            assertThrows(IllegalStateException.class,
                    () -> service.handlePrivateTripRejected(EVENT_ID, RECENT, rejection("x")));

            verify(legRepository, never()).deleteUnlinkedByRentalId(any());
            verifyNoInteractions(eventMailer);
            verify(processedEventRepository, never()).save(any());
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            service.handlePrivateTripRejected(EVENT_ID, RECENT, rejection("x"));

            verifyNoInteractions(legRepository, eventMailer);
            verify(processedEventRepository, never()).save(any());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.TripNotificationServiceImplTest#incompleteRejections")
        void incompleteEvent_throwsAndSavesNothing(Instant occurredAt, PrivateTripRejectedEvent.Payload payload) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.handlePrivateTripRejected(EVENT_ID, occurredAt, payload));

            verifyNoInteractions(legRepository, eventMailer);
            verify(processedEventRepository, never()).save(any());
        }
    }

    static Stream<Arguments> incompleteUpserts() {
        return Stream.of(
                Arguments.of(null, regular(DEPARTURE, ARRIVAL, "P-001", DRIVER_ID)),
                Arguments.of(RECENT, null),
                Arguments.of(RECENT, new TripUpsertEvent.Payload(null, "REGULAR", "Central", "Xela", null, null,
                        BUS_ID, "P-001", DRIVER_ID, DEPARTURE, ARRIVAL, "SCHEDULED")),
                Arguments.of(RECENT, regular(null, ARRIVAL, "P-001", DRIVER_ID)),
                Arguments.of(RECENT, regular(DEPARTURE, null, "P-001", DRIVER_ID)));
    }

    static Stream<Arguments> incompleteDeletions() {
        return Stream.of(
                Arguments.of(null, new TripDeletedEvent.Payload(TRIP_ID, "REGULAR", null, null)),
                Arguments.of(RECENT, null),
                Arguments.of(RECENT, new TripDeletedEvent.Payload(null, "REGULAR", null, null)));
    }

    static Stream<Arguments> incompleteRejections() {
        return Stream.of(
                Arguments.of(null, rejection("x")),
                Arguments.of(RECENT, null),
                Arguments.of(RECENT, new PrivateTripRejectedEvent.Payload(null, BUS_ID, DRIVER_ID, "X", "x")));
    }

    private void assertMarkedProcessed() {
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
    }

    private static void assertSkipped(MailRequest mail, String template) {
        assertEquals(template, mail.template());
        assertTrue(mail.recipients().isEmpty());
        assertNull(mail.view());
        assertEquals("Evento de hace más de PT48H", mail.skipReason());
    }

    private static TripUpsertEvent.Payload regular(LocalDateTime departure, LocalDateTime arrival, String plate,
                                                   UUID driverId) {
        return new TripUpsertEvent.Payload(TRIP_ID, "REGULAR", "Central", "Xela", null, null, BUS_ID, plate, driverId,
                departure, arrival, "SCHEDULED");
    }

    private static TripUpsertEvent.Payload privatePayload() {
        return new TripUpsertEvent.Payload(TRIP_ID, "PRIVATE", null, " ", "Hotel Antigua", "Lago Atitlan", BUS_ID,
                "P-001", DRIVER_ID, DEPARTURE, ARRIVAL, "SCHEDULED");
    }

    private static PrivateTripRejectedEvent.Payload rejection(String reason) {
        return new PrivateTripRejectedEvent.Payload(RENTAL_ID, BUS_ID, DRIVER_ID, "BUS_OVERLAP", reason);
    }

    private static TripDirectoryEntity trip(TripKind kind, String plate, UUID driverId, Instant lastEventAt) {
        return TripDirectoryEntity.builder()
                .tripId(TRIP_ID)
                .type(kind)
                .originName("Central")
                .destinationName("Xela")
                .departureAt(DEPARTURE)
                .estimatedArrival(ARRIVAL)
                .busId(BUS_ID)
                .busPlate(plate)
                .driverId(driverId)
                .status("SCHEDULED")
                .lastEventAt(lastEventAt)
                .build();
    }

    private static DriverDirectoryEntity driver(UUID driverId, String name) {
        return DriverDirectoryEntity.builder()
                .driverId(driverId)
                .fullName(name)
                .email(name.split(" ")[0].toLowerCase() + "@mail.com")
                .branchId(BRANCH_ID)
                .licenseNumber("LIC-001")
                .licenseExpiresOn(LocalDate.now(ZONE).plusYears(1))
                .active(true)
                .lastEventAt(RECENT)
                .build();
    }

    private static TripPassengerEntity passenger(String email, String seat) {
        return TripPassengerEntity.builder()
                .ticketId(UUID.randomUUID())
                .tripId(TRIP_ID)
                .orderId(UUID.randomUUID())
                .email(email)
                .seatLabel(seat)
                .build();
    }

    private static PrivateTripLegEntity leg() {
        return PrivateTripLegEntity.builder()
                .id(UUID.randomUUID())
                .rentalId(RENTAL_ID)
                .legNo(1)
                .branchId(BRANCH_ID)
                .customerUserId(CUSTOMER_USER_ID)
                .busId(BUS_ID)
                .driverId(DRIVER_ID)
                .originText("Hotel Antigua")
                .destinationText("Lago Atitlan")
                .departureAt(DEPARTURE)
                .estimatedArrival(ARRIVAL)
                .build();
    }

    private static String rowValue(MailRequest request, String label) {
        return request.view().getRows().stream().filter(row -> row.getLabel().equals(label))
                .map(NoticeRow::getValue).findFirst().orElse(null);
    }
}
