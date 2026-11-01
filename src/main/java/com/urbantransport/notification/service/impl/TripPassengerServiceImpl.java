package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.domain.TripPassengerEntity;
import com.urbantransport.notification.event.TicketPurchasedEvent;
import com.urbantransport.notification.repository.TripPassengerRepository;
import com.urbantransport.notification.service.TripPassengerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TripPassengerServiceImpl implements TripPassengerService {

    private final TripPassengerRepository tripPassengerRepository;

    @Override
    @Transactional
    public void record(TicketPurchasedEvent.Payload payload) {
        if (payload == null || payload.buyerEmail() == null || payload.buyerEmail().isBlank()
                || payload.trip() == null || payload.trip().tripId() == null || payload.tickets() == null) {
            return;
        }
        String email = payload.buyerEmail().trim().toLowerCase(Locale.ROOT);
        List<TripPassengerEntity> rows = payload.tickets().stream()
                .map(ticket -> TripPassengerEntity.builder()
                        .ticketId(ticket.ticketId())
                        .tripId(payload.trip().tripId())
                        .orderId(payload.orderId())
                        .email(email)
                        .customerNit(payload.customerNit())
                        .seatLabel(ticket.seatLabel())
                        .build())
                .toList();
        tripPassengerRepository.saveAll(rows);
    }

    @Override
    @Transactional
    public void remove(UUID ticketId) {
        tripPassengerRepository.findById(ticketId).ifPresent(tripPassengerRepository::delete);
    }
}