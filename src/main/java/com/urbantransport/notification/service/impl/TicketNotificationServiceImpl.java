package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationEventProperties;
import com.urbantransport.notification.domain.ProcessedEventEntity;
import com.urbantransport.notification.domain.Role;
import com.urbantransport.notification.domain.UserDirectoryEntity;
import com.urbantransport.notification.dto.TicketLineView;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.event.TicketCancelledEvent;
import com.urbantransport.notification.event.TicketPurchasedEvent;
import com.urbantransport.notification.repository.ProcessedEventRepository;
import com.urbantransport.notification.repository.UserDirectoryRepository;
import com.urbantransport.notification.service.EmailQueueService;
import com.urbantransport.notification.service.EmailRenderer;
import com.urbantransport.notification.service.EmailRequest;
import com.urbantransport.notification.service.TicketNotificationService;
import com.urbantransport.notification.util.EmailFormats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Decide qué correos genera cada evento. Todo (los correos encolados y el registro del eventId) va en una sola
 * transacción. Un evento incompleto lanza excepción para que el mensaje termine en el DLT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketNotificationServiceImpl implements TicketNotificationService {

    private static final String TEMPLATE_PURCHASED = "ticket-purchased";
    private static final String TEMPLATE_CANCELLED_WALLET = "ticket-cancelled-wallet";
    private static final String TEMPLATE_CANCELLED_CASH = "ticket-cancelled-cash";

    private static final String REFUND_CASH = "CASH";
    private static final String CHANNEL_COUNTER = "COUNTER";

    private final ProcessedEventRepository processedEventRepository;
    private final UserDirectoryRepository userDirectoryRepository;
    private final EmailQueueService emailQueueService;
    private final EmailRenderer emailRenderer;
    private final NotificationEventProperties eventProperties;
    private final Clock clock;

    // ---------- compra ----------

    @Override
    @Transactional
    public void handleTicketPurchased(UUID eventId, Instant occurredAt, TicketPurchasedEvent.Payload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }
        if (occurredAt == null || payload == null || payload.orderId() == null || payload.trip() == null
                || payload.trip().departureAt() == null || payload.tickets() == null || payload.tickets().isEmpty()) {
            throw new IllegalArgumentException("TicketPurchased incompleto: " + eventId);
        }

        TicketPurchasedEvent.Trip trip = payload.trip();
        String route = trip.originName() + " → " + trip.destinationName();
        String subject = "Tu compra de boletos: " + route + " (" + EmailFormats.shortDateTime(trip.departureAt()) + ")";

        if (isTooOld(occurredAt, eventProperties.maxPurchaseAge())) {
            skip(eventId, EventTypes.TICKET_PURCHASED, payload.orderId(), TEMPLATE_PURCHASED, subject,
                    "Compra de hace más de " + eventProperties.maxPurchaseAge());
        } else if (isBlank(payload.buyerEmail())) {
            skip(eventId, EventTypes.TICKET_PURCHASED, payload.orderId(), TEMPLATE_PURCHASED, subject,
                    CHANNEL_COUNTER.equals(payload.channel())
                            ? "Venta en ventanilla: no hay correo del cliente"
                            : "La compra no trae correo del comprador");
        } else {
            List<TicketLineView> lines = payload.tickets().stream()
                    .map(ticket -> new TicketLineView(ticket.seatLabel(), EmailFormats.money(ticket.pricePaid())))
                    .toList();

            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("customerNit", payload.customerNit());
            variables.put("origin", trip.originName());
            variables.put("destination", trip.destinationName());
            variables.put("departure", EmailFormats.longDateTime(trip.departureAt()));
            variables.put("busPlate", trip.busPlate());
            variables.put("orderCode", EmailFormats.shortCode(payload.orderId()));
            variables.put("tickets", lines);
            variables.put("total", EmailFormats.money(payload.total()));
            variables.put("paymentDate", EmailFormats.date(payload.paymentDate()));

            emailQueueService.enqueue(new EmailRequest(eventId, EventTypes.TICKET_PURCHASED, payload.orderId(),
                    normalize(payload.buyerEmail()), TEMPLATE_PURCHASED, subject,
                    emailRenderer.render(TEMPLATE_PURCHASED, variables)));
        }
        markProcessed(eventId);
    }

    // ---------- cancelación ----------

    @Override
    @Transactional
    public void handleTicketCancelled(UUID eventId, Instant occurredAt, TicketCancelledEvent.Payload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }
        if (occurredAt == null || payload == null || payload.ticketId() == null || payload.orderId() == null
                || payload.departureAt() == null) {
            throw new IllegalArgumentException("TicketCancelled incompleto: " + eventId);
        }

        boolean cash = REFUND_CASH.equals(payload.refundMethod());
        String template = cash ? TEMPLATE_CANCELLED_CASH : TEMPLATE_CANCELLED_WALLET;
        String route = payload.originName() + " → " + payload.destinationName();
        String subject = cash
                ? "Reembolso en efectivo pendiente: asiento " + payload.seatLabel() + ", " + route
                : "Tu boleto fue cancelado: asiento " + payload.seatLabel() + ", " + route;

        if (isTooOld(occurredAt, eventProperties.maxCancellationAge())) {
            skip(eventId, EventTypes.TICKET_CANCELLED, payload.ticketId(), template, subject,
                    "Cancelación de hace más de " + eventProperties.maxCancellationAge());
        } else {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("customerNit", payload.customerNit());
            variables.put("origin", payload.originName());
            variables.put("destination", payload.destinationName());
            variables.put("departure", EmailFormats.longDateTime(payload.departureAt()));
            variables.put("seat", payload.seatLabel());
            variables.put("amount", EmailFormats.money(payload.pricePaid()));
            variables.put("orderCode", EmailFormats.shortCode(payload.orderId()));
            variables.put("reason", reasonLabel(payload.reason()));

            if (cash) {
                notifyBranchAdmins(eventId, payload, template, subject, variables);
            } else {
                notifyBuyer(eventId, payload, template, subject, variables);
            }
        }
        markProcessed(eventId);
    }

    private void notifyBuyer(UUID eventId, TicketCancelledEvent.Payload payload, String template, String subject,
                             Map<String, Object> variables) {
        if (isBlank(payload.buyerEmail())) {
            skip(eventId, EventTypes.TICKET_CANCELLED, payload.ticketId(), template, subject,
                    "La compra no trae correo del comprador");
            return;
        }
        emailQueueService.enqueue(new EmailRequest(eventId, EventTypes.TICKET_CANCELLED, payload.ticketId(),
                normalize(payload.buyerEmail()), template, subject, emailRenderer.render(template, variables)));
    }

    /** Un correo por administrador de la sucursal; si el directorio no tiene ninguno, se avisa a los SYSTEM_ADMIN. */
    private void notifyBranchAdmins(UUID eventId, TicketCancelledEvent.Payload payload, String template,
                                    String subject, Map<String, Object> variables) {
        List<String> recipients = adminEmails(payload);
        if (recipients.isEmpty()) {
            log.warn("Reembolso en efectivo del boleto {}: no hay administradores activos a quién avisar",
                    payload.ticketId());
            skip(eventId, EventTypes.TICKET_CANCELLED, payload.ticketId(), template, subject,
                    "No hay administradores activos en el directorio");
            return;
        }
        String body = emailRenderer.render(template, variables);
        for (String recipient : recipients) {
            emailQueueService.enqueue(new EmailRequest(eventId, EventTypes.TICKET_CANCELLED, payload.ticketId(),
                    recipient, template, subject, body));
        }
    }

    private List<String> adminEmails(TicketCancelledEvent.Payload payload) {
        List<UserDirectoryEntity> admins = payload.branchId() == null
                ? List.of()
                : userDirectoryRepository.findByBranchIdAndRoleAndActiveTrue(payload.branchId(), Role.BRANCH_ADMIN);
        if (admins.isEmpty()) {
            admins = userDirectoryRepository.findByRoleAndActiveTrue(Role.SYSTEM_ADMIN);
        }
        return admins.stream().map(UserDirectoryEntity::getEmail).distinct().toList();
    }

    // ---------- utilidades ----------

    private void skip(UUID eventId, String eventType, UUID referenceId, String template, String subject,
                      String reason) {
        emailQueueService.skip(eventId, eventType, referenceId, template, subject, reason);
    }

    private boolean isTooOld(Instant occurredAt, Duration maxAge) {
        return Duration.between(occurredAt, Instant.now(clock)).compareTo(maxAge) > 0;
    }

    private void markProcessed(UUID eventId) {
        processedEventRepository.save(ProcessedEventEntity.builder().eventId(eventId).build());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String reasonLabel(String reason) {
        return "TRIP_DELETED".equals(reason) ? "El viaje fue cancelado" : "El boleto fue cancelado";
    }
}