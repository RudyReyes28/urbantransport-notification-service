package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationEventProperties;
import com.urbantransport.notification.domain.DriverDirectoryEntity;
import com.urbantransport.notification.domain.PrivateTripLegEntity;
import com.urbantransport.notification.domain.ProcessedEventEntity;
import com.urbantransport.notification.domain.TripDirectoryEntity;
import com.urbantransport.notification.domain.TripKind;
import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.Tone;
import com.urbantransport.notification.event.RentalAssignedEvent;
import com.urbantransport.notification.repository.DriverDirectoryRepository;
import com.urbantransport.notification.repository.PrivateTripLegRepository;
import com.urbantransport.notification.repository.ProcessedEventRepository;
import com.urbantransport.notification.repository.TripDirectoryRepository;
import com.urbantransport.notification.service.EventMailer;
import com.urbantransport.notification.service.MailRequest;
import com.urbantransport.notification.service.RecipientResolver;
import com.urbantransport.notification.util.EmailFormats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateTripServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant RECENT = NOW.minusSeconds(60);
    private static final Instant STALE = NOW.minus(3, ChronoUnit.DAYS);
    private static final LocalDateTime DEPARTURE = LocalDate.now(ZONE).plusDays(5).atTime(8, 0);
    private static final LocalDateTime RETURN = DEPARTURE.plusDays(1).withHour(16);
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RENTAL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BRANCH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID BUS_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID DRIVER_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID CUSTOMER_USER_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID TRIP_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID RETURN_TRIP_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final NotificationEventProperties PROPERTIES = new NotificationEventProperties(Duration.ofHours(24),
            Duration.ofDays(7), Duration.ofDays(2), Duration.ofDays(2), Duration.ofDays(2), Duration.ofDays(2));

    @Mock
    private PrivateTripLegRepository legRepository;

    @Mock
    private TripDirectoryRepository tripRepository;

    @Mock
    private DriverDirectoryRepository driverRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private RecipientResolver recipientResolver;

    @Mock
    private EventMailer eventMailer;

    @Captor
    private ArgumentCaptor<PrivateTripLegEntity> legCaptor;

    @Captor
    private ArgumentCaptor<MailRequest> mailCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEventEntity> processedEventCaptor;

    private PrivateTripServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PrivateTripServiceImpl(legRepository, tripRepository, driverRepository, processedEventRepository,
                recipientResolver, eventMailer, PROPERTIES, CLOCK);
    }

    @Nested
    class RecordAssignment {

        @Test
        void tripsNotYetScheduled_savesLegsInGuatemalaTimeAndWaits() {
            when(legRepository.save(any(PrivateTripLegEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tripRepository.findUnlinkedPrivate(any(), any(), any())).thenReturn(List.of());
            when(legRepository.findByRentalIdOrderByLegNoAsc(RENTAL_ID))
                    .thenReturn(List.of(leg(1, DEPARTURE, null), leg(2, RETURN, null)));

            service.recordAssignment(EVENT_ID, RECENT, payload(List.of(
                    rentalLeg("Central", "Xela", DEPARTURE.plusSeconds(42)), rentalLeg("Xela", "Central", RETURN))));

            verify(legRepository).deleteUnlinkedByRentalId(RENTAL_ID);
            verify(legRepository, times(2)).save(legCaptor.capture());
            PrivateTripLegEntity first = legCaptor.getAllValues().getFirst();
            assertEquals(RENTAL_ID, first.getRentalId());
            assertEquals(1, first.getLegNo());
            assertEquals(BRANCH_ID, first.getBranchId());
            assertEquals(CUSTOMER_USER_ID, first.getCustomerUserId());
            assertEquals("1234567", first.getCustomerNit());
            assertEquals(BUS_ID, first.getBusId());
            assertEquals(DRIVER_ID, first.getDriverId());
            assertEquals("Central", first.getOriginText());
            assertEquals("Xela", first.getDestinationText());
            assertEquals(DEPARTURE, first.getDepartureAt());
            assertEquals(DEPARTURE.plusHours(4), first.getEstimatedArrival());
            assertNull(first.getTripId());
            PrivateTripLegEntity second = legCaptor.getAllValues().getLast();
            assertEquals(2, second.getLegNo());
            assertEquals(RETURN, second.getDepartureAt());
            verify(tripRepository).findUnlinkedPrivate(BUS_ID, DRIVER_ID, DEPARTURE);
            verify(tripRepository).findUnlinkedPrivate(BUS_ID, DRIVER_ID, RETURN);
            verifyNoInteractions(eventMailer);
            verify(legRepository, never()).saveAll(any());
            assertMarkedProcessed();
        }

        @Test
        void tripAlreadyScheduled_isLinkedAndBothAreNotified() {
            TripDirectoryEntity trip = trip(TRIP_ID, DEPARTURE, "P-001");
            when(legRepository.save(any(PrivateTripLegEntity.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tripRepository.findUnlinkedPrivate(BUS_ID, DRIVER_ID, DEPARTURE)).thenReturn(List.of(trip));
            PrivateTripLegEntity linked = leg(1, DEPARTURE, TRIP_ID);
            when(legRepository.findByRentalIdOrderByLegNoAsc(RENTAL_ID)).thenReturn(List.of(linked));
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver()));
            when(recipientResolver.userEmail(CUSTOMER_USER_ID)).thenReturn(List.of("cliente@mail.com"));

            service.recordAssignment(EVENT_ID, RECENT, payload(List.of(rentalLeg("Central", "Xela", DEPARTURE))));

            verify(legRepository, times(2)).save(legCaptor.capture());
            assertSame(legCaptor.getAllValues().getFirst(), legCaptor.getAllValues().getLast());
            assertEquals(TRIP_ID, legCaptor.getValue().getTripId());
            assertEquals(RENTAL_ID, trip.getRentalId());
            verify(tripRepository).save(trip);
            verify(eventMailer, times(2)).send(mailCaptor.capture());
            UUID expectedEventId = UUID.nameUUIDFromBytes((RENTAL_ID + "|" + TRIP_ID).getBytes(StandardCharsets.UTF_8));
            MailRequest customer = mailCaptor.getAllValues().getFirst();
            assertEquals(expectedEventId, customer.eventId());
            assertEquals("PrivateTripScheduled", customer.eventType());
            assertEquals(RENTAL_ID, customer.referenceId());
            assertEquals("private-trip-customer", customer.template());
            assertEquals("Tu viaje privado está programado: Central → Xela", customer.subject());
            assertEquals(List.of("cliente@mail.com"), customer.recipients());
            assertEquals(Tone.SUCCESS, customer.view().getTone());
            assertTrue(rowValue(customer, "Viaje").startsWith("Central → Xela · salida "
                    + EmailFormats.shortDateTime(DEPARTURE)));
            assertEquals("P-001", rowValue(customer, "Bus"));
            assertEquals("Juan Perez", rowValue(customer, "Chofer"));
            MailRequest driverMail = mailCaptor.getAllValues().getLast();
            assertEquals("private-trip-driver", driverMail.template());
            assertEquals("Nuevo viaje privado: Central → Xela, " + EmailFormats.shortDateTime(DEPARTURE),
                    driverMail.subject());
            assertEquals(List.of("juan@mail.com"), driverMail.recipients());
            assertNull(rowValue(driverMail, "Chofer"));
            assertEquals(NOW, linked.getNotifiedAt());
            verify(legRepository).saveAll(List.of(linked));
            assertMarkedProcessed();
        }

        @Test
        void alreadyProcessedEvent_isIgnored() {
            when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

            service.recordAssignment(EVENT_ID, RECENT, payload(List.of(rentalLeg("Central", "Xela", DEPARTURE))));

            verifyNoInteractions(legRepository, tripRepository, eventMailer);
            verify(processedEventRepository, never()).save(any());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.PrivateTripServiceImplTest#incompleteAssignments")
        void incompleteAssignment_throwsAndSavesNothing(Instant occurredAt, RentalAssignedEvent.Payload payload) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.recordAssignment(EVENT_ID, occurredAt, payload));

            verifyNoInteractions(legRepository, tripRepository, eventMailer);
            verify(processedEventRepository, never()).save(any());
        }

        @Test
        void legWithoutDates_throwsBeforeSavingIt() {
            RentalAssignedEvent.Leg broken = new RentalAssignedEvent.Leg("Central", "Xela", null, null);

            assertThrows(IllegalArgumentException.class,
                    () -> service.recordAssignment(EVENT_ID, RECENT, payload(List.of(broken))));

            verify(legRepository, never()).save(any());
            verifyNoInteractions(eventMailer);
            verify(processedEventRepository, never()).save(any());
        }
    }

    @Nested
    class LinkScheduledTrip {

        @Test
        void tripWithoutAssignment_doesNothing() {
            TripDirectoryEntity trip = trip(TRIP_ID, DEPARTURE, "P-001");
            when(legRepository.findUnlinked(BUS_ID, DRIVER_ID, DEPARTURE)).thenReturn(List.of());

            service.linkScheduledTrip(RECENT, trip);

            verify(legRepository, never()).save(any());
            verify(tripRepository, never()).save(any());
            verifyNoInteractions(eventMailer);
            assertNull(trip.getRentalId());
        }

        @Test
        void firstOfTwoLegs_isLinkedButWaitsForTheReturn() {
            TripDirectoryEntity trip = trip(TRIP_ID, DEPARTURE, "P-001");
            PrivateTripLegEntity outbound = leg(1, DEPARTURE, null);
            when(legRepository.findUnlinked(BUS_ID, DRIVER_ID, DEPARTURE)).thenReturn(List.of(outbound));
            when(legRepository.findByRentalIdOrderByLegNoAsc(RENTAL_ID))
                    .thenReturn(List.of(outbound, leg(2, RETURN, null)));

            service.linkScheduledTrip(RECENT, trip);

            assertEquals(TRIP_ID, outbound.getTripId());
            assertEquals(RENTAL_ID, trip.getRentalId());
            verify(legRepository).save(outbound);
            verify(tripRepository).save(trip);
            verifyNoInteractions(eventMailer);
        }

        @Test
        void lastLegOfRoundTrip_notifiesWithOutboundAndReturnRows() {
            TripDirectoryEntity returnTrip = trip(RETURN_TRIP_ID, RETURN, "P-001");
            PrivateTripLegEntity outbound = leg(1, DEPARTURE, TRIP_ID);
            PrivateTripLegEntity back = leg(2, RETURN, null);
            when(legRepository.findUnlinked(BUS_ID, DRIVER_ID, RETURN)).thenReturn(List.of(back));
            when(legRepository.findByRentalIdOrderByLegNoAsc(RENTAL_ID)).thenReturn(List.of(outbound, back));
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip(TRIP_ID, DEPARTURE, "P-001")));
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver()));
            when(recipientResolver.userEmail(CUSTOMER_USER_ID)).thenReturn(List.of("cliente@mail.com"));

            service.linkScheduledTrip(RECENT, returnTrip);

            verify(eventMailer, times(2)).send(mailCaptor.capture());
            MailRequest customer = mailCaptor.getAllValues().getFirst();
            UUID expectedEventId = UUID.nameUUIDFromBytes((RENTAL_ID + "|" + TRIP_ID + "|" + RETURN_TRIP_ID)
                    .getBytes(StandardCharsets.UTF_8));
            assertEquals(expectedEventId, customer.eventId());
            assertTrue(rowValue(customer, "Ida").startsWith("Central → Xela"));
            assertTrue(rowValue(customer, "Regreso").startsWith("Xela → Central"));
            assertNull(rowValue(customer, "Viaje"));
            assertEquals(NOW, outbound.getNotifiedAt());
            assertEquals(NOW, back.getNotifiedAt());
        }

        @ParameterizedTest
        @NullAndEmptySource
        void tripWithoutPlateAndUnknownDriver_showsDashesAndSkipsDriver(String plate) {
            TripDirectoryEntity trip = trip(TRIP_ID, DEPARTURE, plate);
            PrivateTripLegEntity leg = leg(1, DEPARTURE, null);
            when(legRepository.findUnlinked(BUS_ID, DRIVER_ID, DEPARTURE)).thenReturn(List.of(leg));
            when(legRepository.findByRentalIdOrderByLegNoAsc(RENTAL_ID)).thenReturn(List.of(leg));
            when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
            when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());
            when(recipientResolver.userEmail(CUSTOMER_USER_ID)).thenReturn(List.of());

            service.linkScheduledTrip(RECENT, trip);

            verify(eventMailer, times(2)).send(mailCaptor.capture());
            MailRequest customer = mailCaptor.getAllValues().getFirst();
            assertEquals("—", rowValue(customer, "Bus"));
            assertEquals("—", rowValue(customer, "Chofer"));
            assertEquals("El alquiler no tiene un cliente con correo en el directorio (venta en mostrador)",
                    customer.skipReason());
            MailRequest driverMail = mailCaptor.getAllValues().getLast();
            assertTrue(driverMail.recipients().isEmpty());
            assertEquals("El chofer no está en el directorio", driverMail.skipReason());
            assertEquals("Hola —, se te asignó un viaje privado.", driverMail.view().getIntro());
        }

        @Test
        void staleTrip_recordsSkipsAndMarksLegsNotified() {
            TripDirectoryEntity trip = trip(TRIP_ID, DEPARTURE, "P-001");
            PrivateTripLegEntity leg = leg(1, DEPARTURE, null);
            when(legRepository.findUnlinked(BUS_ID, DRIVER_ID, DEPARTURE)).thenReturn(List.of(leg));
            when(legRepository.findByRentalIdOrderByLegNoAsc(RENTAL_ID)).thenReturn(List.of(leg));

            service.linkScheduledTrip(STALE, trip);

            verify(eventMailer, times(2)).send(mailCaptor.capture());
            for (MailRequest mail : mailCaptor.getAllValues()) {
                assertTrue(mail.recipients().isEmpty());
                assertNull(mail.view());
                assertEquals("Evento de hace más de PT48H", mail.skipReason());
            }
            assertEquals(List.of("private-trip-customer", "private-trip-driver"),
                    mailCaptor.getAllValues().stream().map(MailRequest::template).toList());
            verifyNoInteractions(recipientResolver, driverRepository);
            assertEquals(NOW, leg.getNotifiedAt());
        }

        @Test
        void alreadyNotifiedRental_isNotNotifiedAgain() {
            TripDirectoryEntity trip = trip(TRIP_ID, DEPARTURE, "P-001");
            PrivateTripLegEntity leg = leg(1, DEPARTURE, null);
            PrivateTripLegEntity notified = leg(2, RETURN, RETURN_TRIP_ID);
            notified.setNotifiedAt(NOW.minusSeconds(3600));
            when(legRepository.findUnlinked(BUS_ID, DRIVER_ID, DEPARTURE)).thenReturn(List.of(leg));
            when(legRepository.findByRentalIdOrderByLegNoAsc(RENTAL_ID)).thenReturn(List.of(leg, notified));

            service.linkScheduledTrip(RECENT, trip);

            verifyNoInteractions(eventMailer);
            verify(legRepository, never()).saveAll(any());
        }

        @Test
        void rentalWithoutLegs_isNotNotified() {
            TripDirectoryEntity trip = trip(TRIP_ID, DEPARTURE, "P-001");
            PrivateTripLegEntity leg = leg(1, DEPARTURE, null);
            when(legRepository.findUnlinked(BUS_ID, DRIVER_ID, DEPARTURE)).thenReturn(List.of(leg));
            when(legRepository.findByRentalIdOrderByLegNoAsc(RENTAL_ID)).thenReturn(List.of());

            service.linkScheduledTrip(RECENT, trip);

            assertSame(TRIP_ID, leg.getTripId());
            verifyNoInteractions(eventMailer);
        }
    }

    static Stream<Arguments> incompleteAssignments() {
        List<RentalAssignedEvent.Leg> legs = List.of(rentalLeg("Central", "Xela", DEPARTURE));
        return Stream.of(
                Arguments.of(null, payload(legs)),
                Arguments.of(RECENT, null),
                Arguments.of(RECENT, new RentalAssignedEvent.Payload(null, BRANCH_ID, BUS_ID, DRIVER_ID, 30, null,
                        null, legs)),
                Arguments.of(RECENT, new RentalAssignedEvent.Payload(RENTAL_ID, null, BUS_ID, DRIVER_ID, 30, null,
                        null, legs)),
                Arguments.of(RECENT, new RentalAssignedEvent.Payload(RENTAL_ID, BRANCH_ID, null, DRIVER_ID, 30, null,
                        null, legs)),
                Arguments.of(RECENT, new RentalAssignedEvent.Payload(RENTAL_ID, BRANCH_ID, BUS_ID, null, 30, null,
                        null, legs)),
                Arguments.of(RECENT, payload(null)),
                Arguments.of(RECENT, payload(new ArrayList<>())));
    }

    private void assertMarkedProcessed() {
        verify(processedEventRepository).save(processedEventCaptor.capture());
        assertEquals(EVENT_ID, processedEventCaptor.getValue().getEventId());
    }

    private static RentalAssignedEvent.Payload payload(List<RentalAssignedEvent.Leg> legs) {
        return new RentalAssignedEvent.Payload(RENTAL_ID, BRANCH_ID, BUS_ID, DRIVER_ID, 30, "1234567",
                CUSTOMER_USER_ID, legs);
    }

    private static RentalAssignedEvent.Leg rentalLeg(String origin, String destination, LocalDateTime departure) {
        return new RentalAssignedEvent.Leg(origin, destination, departure.atZone(ZONE).toInstant(),
                departure.plusHours(4).atZone(ZONE).toInstant());
    }

    private static PrivateTripLegEntity leg(int legNo, LocalDateTime departure, UUID tripId) {
        boolean outbound = legNo == 1;
        return PrivateTripLegEntity.builder()
                .id(UUID.randomUUID())
                .rentalId(RENTAL_ID)
                .legNo(legNo)
                .branchId(BRANCH_ID)
                .customerUserId(CUSTOMER_USER_ID)
                .busId(BUS_ID)
                .driverId(DRIVER_ID)
                .originText(outbound ? "Central" : "Xela")
                .destinationText(outbound ? "Xela" : "Central")
                .departureAt(departure)
                .estimatedArrival(departure.plusHours(4))
                .tripId(tripId)
                .build();
    }

    private static TripDirectoryEntity trip(UUID tripId, LocalDateTime departure, String plate) {
        return TripDirectoryEntity.builder()
                .tripId(tripId)
                .type(TripKind.PRIVATE)
                .departureAt(departure)
                .estimatedArrival(departure.plusHours(4))
                .busId(BUS_ID)
                .busPlate(plate)
                .driverId(DRIVER_ID)
                .status("SCHEDULED")
                .lastEventAt(RECENT)
                .build();
    }

    private static DriverDirectoryEntity driver() {
        return DriverDirectoryEntity.builder()
                .driverId(DRIVER_ID)
                .fullName("Juan Perez")
                .email("juan@mail.com")
                .branchId(BRANCH_ID)
                .licenseNumber("LIC-001")
                .licenseExpiresOn(LocalDate.now(ZONE).plusYears(1))
                .active(true)
                .lastEventAt(RECENT)
                .build();
    }

    private static String rowValue(MailRequest request, String label) {
        return request.view().getRows().stream().filter(row -> row.getLabel().equals(label))
                .map(NoticeRow::getValue).findFirst().orElse(null);
    }
}
