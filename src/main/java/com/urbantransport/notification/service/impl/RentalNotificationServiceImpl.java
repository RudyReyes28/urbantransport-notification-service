package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationEventProperties;
import com.urbantransport.notification.domain.ProcessedEventEntity;
import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.NoticeView;
import com.urbantransport.notification.dto.Tone;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.event.RentalEvent;
import com.urbantransport.notification.repository.ProcessedEventRepository;
import com.urbantransport.notification.service.EventMailer;
import com.urbantransport.notification.service.MailRequest;
import com.urbantransport.notification.service.RecipientResolver;
import com.urbantransport.notification.service.RentalNotificationService;
import com.urbantransport.notification.util.EmailFormats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentalNotificationServiceImpl implements RentalNotificationService {

    private static final String TEMPLATE_REQUESTED_ADMIN = "rental-requested-admin";
    private static final String TEMPLATE_REQUESTED_CUSTOMER = "rental-requested-customer";
    private static final String TEMPLATE_QUOTED = "rental-quoted";
    private static final String TEMPLATE_REJECTED = "rental-rejected";
    private static final String TEMPLATE_PAID_CUSTOMER = "rental-paid-customer";
    private static final String TEMPLATE_PAID_ADMIN = "rental-paid-admin";

    private static final String CHANNEL_ONLINE = "ONLINE";
    private static final String NO_CUSTOMER_EMAIL =
            "El alquiler no tiene un cliente con cuenta y correo en el directorio (venta en mostrador)";
    private static final String NO_ADMINS = "No hay administradores activos en el directorio";

    private final ProcessedEventRepository processedEventRepository;
    private final RecipientResolver recipientResolver;
    private final EventMailer eventMailer;
    private final NotificationEventProperties eventProperties;
    private final Clock clock;

    // ---------- solicitud ----------

    @Override
    @Transactional
    public void handleRequested(UUID eventId, Instant occurredAt, RentalEvent.Payload payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        validate(eventId, occurredAt, payload);
        String type = EventTypes.RENTAL_REQUESTED;
        String adminSubject = "Nueva solicitud de alquiler: " + route(payload);
        String customerSubject = "Recibimos tu solicitud de alquiler";

        if (!CHANNEL_ONLINE.equals(payload.channel())) {
            log.debug("Alquiler {} presencial: no hay solicitud que avisar", payload.rentalId());
        } else if (isTooOld(occurredAt)) {
            skipStale(eventId, type, payload, TEMPLATE_REQUESTED_ADMIN, adminSubject);
        } else {
            NoticeView adminView = NoticeView.of(Tone.INFO, "Nueva solicitud de alquiler",
                    "Un cliente solicitó un alquiler en tu sucursal y espera tu cotización.",
                    rows(payload, "Precio estimado", payload.estimatedPrice()),
                    "Entra a Alquileres para confirmar o modificar el precio.");
            eventMailer.send(new MailRequest(eventId, type, payload.rentalId(), TEMPLATE_REQUESTED_ADMIN,
                    adminSubject, adminView, recipientResolver.branchAdmins(payload.branchId()), NO_ADMINS));

            NoticeView customerView = NoticeView.of(Tone.INFO, "Recibimos tu solicitud",
                    "Hola, recibimos tu solicitud de alquiler. La sucursal revisará el precio y te avisaremos.",
                    rows(payload, "Precio estimado", payload.estimatedPrice()),
                    "El precio puede cambiar al confirmarse.");
            eventMailer.send(new MailRequest(eventId, type, payload.rentalId(), TEMPLATE_REQUESTED_CUSTOMER,
                    customerSubject, customerView, recipientResolver.userEmail(payload.customerUserId()),
                    NO_CUSTOMER_EMAIL));
        }
        markProcessed(eventId);
    }

    // ---------- cotización ----------

    @Override
    @Transactional
    public void handleQuoted(UUID eventId, Instant occurredAt, RentalEvent.Payload payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        validate(eventId, occurredAt, payload);
        String subject = "Tu alquiler fue cotizado: " + EmailFormats.money(payload.confirmedPrice());

        if (isTooOld(occurredAt)) {
            skipStale(eventId, EventTypes.RENTAL_QUOTED, payload, TEMPLATE_QUOTED, subject);
        } else {
            NoticeView view = NoticeView.of(Tone.SUCCESS, "Tu alquiler fue cotizado",
                    "Hola, la sucursal confirmó el precio de tu alquiler: "
                            + EmailFormats.money(payload.confirmedPrice()) + ".",
                    rows(payload, "Precio final", payload.confirmedPrice()),
                    "Para confirmarlo, págalo con tu cartera desde tu cuenta. Verifica que tengas saldo suficiente.");
            eventMailer.send(new MailRequest(eventId, EventTypes.RENTAL_QUOTED, payload.rentalId(),
                    TEMPLATE_QUOTED, subject, view, recipientResolver.userEmail(payload.customerUserId()),
                    NO_CUSTOMER_EMAIL));
        }
        markProcessed(eventId);
    }

    // ---------- rechazo ----------

    @Override
    @Transactional
    public void handleRejected(UUID eventId, Instant occurredAt, RentalEvent.Payload payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        validate(eventId, occurredAt, payload);
        String subject = "Tu solicitud de alquiler no pudo atenderse";

        if (isTooOld(occurredAt)) {
            skipStale(eventId, EventTypes.RENTAL_REJECTED, payload, TEMPLATE_REJECTED, subject);
        } else {
            List<NoticeRow> rows = new ArrayList<>();
            rows.add(new NoticeRow("Motivo", payload.rejectionReason() == null ? "—" : payload.rejectionReason()));
            rows.addAll(rows(payload, null, null));
            NoticeView view = NoticeView.of(Tone.DANGER, "Solicitud rechazada",
                    "Hola, lamentamos informarte que la sucursal no pudo atender tu solicitud de alquiler.",
                    rows, "Puedes enviar una nueva solicitud con otras fechas.");
            eventMailer.send(new MailRequest(eventId, EventTypes.RENTAL_REJECTED, payload.rentalId(),
                    TEMPLATE_REJECTED, subject, view, recipientResolver.userEmail(payload.customerUserId()),
                    NO_CUSTOMER_EMAIL));
        }
        markProcessed(eventId);
    }

    // ---------- pago ----------

    @Override
    @Transactional
    public void handlePaid(UUID eventId, Instant occurredAt, RentalEvent.Payload payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        validate(eventId, occurredAt, payload);
        String type = EventTypes.RENTAL_PAID;
        String customerSubject = "Pago de alquiler recibido: " + EmailFormats.money(payload.confirmedPrice());
        String adminSubject = "Alquiler pagado, falta asignar bus y chofer: " + route(payload);

        if (isTooOld(occurredAt)) {
            skipStale(eventId, type, payload, TEMPLATE_PAID_CUSTOMER, customerSubject);
        } else {
            List<NoticeRow> rows = rows(payload, "Total pagado", payload.confirmedPrice());
            rows.add(new NoticeRow("Fecha de pago", EmailFormats.date(payload.paymentDate())));

            NoticeView customerView = NoticeView.of(Tone.SUCCESS, "Pago recibido",
                    "Hola, recibimos el pago de tu alquiler. En breve la sucursal asignará el bus y el chofer.",
                    rows, null);
            eventMailer.send(new MailRequest(eventId, type, payload.rentalId(), TEMPLATE_PAID_CUSTOMER,
                    customerSubject, customerView, recipientResolver.userEmail(payload.customerUserId()),
                    NO_CUSTOMER_EMAIL));

            if (CHANNEL_ONLINE.equals(payload.channel())) {
                NoticeView adminView = NoticeView.of(Tone.WARNING, "Alquiler pagado: falta asignar bus y chofer",
                        "El cliente pagó su alquiler. Queda pendiente asignar un bus y un chofer.", rows,
                        "Entra a Alquileres y asigna los recursos para que se programe el viaje.");
                eventMailer.send(new MailRequest(eventId, type, payload.rentalId(), TEMPLATE_PAID_ADMIN,
                        adminSubject, adminView, recipientResolver.branchAdmins(payload.branchId()), NO_ADMINS));
            }
        }
        markProcessed(eventId);
    }

    // ---------- utilidades ----------

    private List<NoticeRow> rows(RentalEvent.Payload payload, String priceLabel, BigDecimal price) {
        List<NoticeRow> rows = new ArrayList<>();
        rows.add(new NoticeRow("NIT del cliente", payload.customerNit() == null ? "—" : payload.customerNit()));
        rows.add(new NoticeRow("Ruta", route(payload)));
        rows.add(new NoticeRow("Salida", EmailFormats.date(payload.departureDate())));
        rows.add(new NoticeRow("Retorno", payload.returnDate() == null
                ? "Solo ida" : EmailFormats.date(payload.returnDate())));
        rows.add(new NoticeRow("Pasajeros", payload.passengers() == null ? "—" : String.valueOf(payload.passengers())));
        if (payload.distanceKm() != null) {
            rows.add(new NoticeRow("Distancia", payload.distanceKm().stripTrailingZeros().toPlainString() + " km"));
        }
        if (priceLabel != null) {
            rows.add(new NoticeRow(priceLabel, EmailFormats.money(price)));
        }
        return rows;
    }

    private String route(RentalEvent.Payload payload) {
        return payload.originText() + " → " + payload.destinationText();
    }

    private void skipStale(UUID eventId, String eventType, RentalEvent.Payload payload, String template,
                           String subject) {
        eventMailer.send(new MailRequest(eventId, eventType, payload.rentalId(), template, subject, null,
                List.of(), "Evento de hace más de " + eventProperties.maxRentalEventAge()));
    }

    private boolean isTooOld(Instant occurredAt) {
        Duration max = eventProperties.maxRentalEventAge();
        return Duration.between(occurredAt, Instant.now(clock)).compareTo(max) > 0;
    }

    private boolean alreadyProcessed(UUID eventId) {
        boolean processed = processedEventRepository.existsById(eventId);
        if (processed) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
        }
        return processed;
    }

    private void validate(UUID eventId, Instant occurredAt, RentalEvent.Payload payload) {
        if (occurredAt == null || payload == null || payload.rentalId() == null || payload.originText() == null
                || payload.destinationText() == null) {
            throw new IllegalArgumentException("Evento de alquiler incompleto: " + eventId);
        }
    }

    private void markProcessed(UUID eventId) {
        processedEventRepository.save(ProcessedEventEntity.builder().eventId(eventId).build());
    }
}