package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.domain.TripPassengerEntity;
import com.urbantransport.notification.event.TicketPurchasedEvent;
import com.urbantransport.notification.repository.TripPassengerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripPassengerServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRIP_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TICKET_A = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID TICKET_B = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final LocalDateTime DEPARTURE = LocalDate.now(ZONE).plusDays(3).atTime(6, 0);

    @Mock
    private TripPassengerRepository tripPassengerRepository;

    @Captor
    private ArgumentCaptor<List<TripPassengerEntity>> rowsCaptor;

    private TripPassengerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TripPassengerServiceImpl(tripPassengerRepository);
    }

    @Nested
    class Record {

        @Test
        void purchaseWithEmail_savesOneRowPerTicketWithNormalizedEmail() {
            service.record(payload(" Ana@Mail.com ", trip(TRIP_ID), tickets()));

            verify(tripPassengerRepository).saveAll(rowsCaptor.capture());
            List<TripPassengerEntity> rows = rowsCaptor.getValue();
            assertEquals(2, rows.size());
            assertEquals(TICKET_A, rows.getFirst().getTicketId());
            assertEquals(TRIP_ID, rows.getFirst().getTripId());
            assertEquals(ORDER_ID, rows.getFirst().getOrderId());
            assertEquals("ana@mail.com", rows.getFirst().getEmail());
            assertEquals("1234567", rows.getFirst().getCustomerNit());
            assertEquals("1A", rows.getFirst().getSeatLabel());
            assertEquals(TICKET_B, rows.getLast().getTicketId());
            assertEquals("1B", rows.getLast().getSeatLabel());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.TripPassengerServiceImplTest#unusablePurchases")
        void purchaseWithoutEmailTripOrTickets_savesNothing(TicketPurchasedEvent.Payload payload) {
            service.record(payload);

            verifyNoInteractions(tripPassengerRepository);
        }
    }

    @Nested
    class Remove {

        @Test
        void knownTicket_isDeleted() {
            TripPassengerEntity passenger = TripPassengerEntity.builder().ticketId(TICKET_A).build();
            when(tripPassengerRepository.findById(TICKET_A)).thenReturn(Optional.of(passenger));

            service.remove(TICKET_A);

            verify(tripPassengerRepository).delete(passenger);
        }

        @Test
        void unknownTicket_deletesNothing() {
            when(tripPassengerRepository.findById(TICKET_A)).thenReturn(Optional.empty());

            service.remove(TICKET_A);

            verify(tripPassengerRepository, never()).delete(any(TripPassengerEntity.class));
        }
    }

    static Stream<TicketPurchasedEvent.Payload> unusablePurchases() {
        return Stream.of(
                null,
                payload(null, trip(TRIP_ID), tickets()),
                payload("  ", trip(TRIP_ID), tickets()),
                payload("ana@mail.com", null, tickets()),
                payload("ana@mail.com", trip(null), tickets()),
                payload("ana@mail.com", trip(TRIP_ID), null));
    }

    private static TicketPurchasedEvent.Payload payload(String email, TicketPurchasedEvent.Trip trip,
                                                        List<TicketPurchasedEvent.Ticket> tickets) {
        return new TicketPurchasedEvent.Payload(ORDER_ID, "ONLINE", null, "1234567", email, LocalDate.now(ZONE),
                new BigDecimal("90.00"), trip, tickets);
    }

    private static TicketPurchasedEvent.Trip trip(UUID tripId) {
        return new TicketPurchasedEvent.Trip(tripId, "Central", "Xela", DEPARTURE, "P-001");
    }

    private static List<TicketPurchasedEvent.Ticket> tickets() {
        return List.of(new TicketPurchasedEvent.Ticket(TICKET_A, 1, "1A", new BigDecimal("45.00")),
                new TicketPurchasedEvent.Ticket(TICKET_B, 2, "1B", new BigDecimal("45.00")));
    }
}
