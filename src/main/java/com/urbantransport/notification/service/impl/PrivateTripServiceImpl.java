package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationEventProperties;
import com.urbantransport.notification.domain.DriverDirectoryEntity;
import com.urbantransport.notification.domain.PrivateTripLegEntity;
import com.urbantransport.notification.domain.ProcessedEventEntity;
import com.urbantransport.notification.domain.TripDirectoryEntity;
import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.NoticeView;
import com.urbantransport.notification.dto.Tone;
import com.urbantransport.notification.event.RentalAssignedEvent;
import com.urbantransport.notification.repository.DriverDirectoryRepository;
import com.urbantransport.notification.repository.PrivateTripLegRepository;
import com.urbantransport.notification.repository.ProcessedEventRepository;
import com.urbantransport.notification.repository.TripDirectoryRepository;
import com.urbantransport.notification.service.EventMailer;
import com.urbantransport.notification.service.MailRequest;
import com.urbantransport.notification.service.PrivateTripService;
import com.urbantransport.notification.service.RecipientResolver;
import com.urbantransport.notification.util.EmailFormats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Trip no dice a qué alquiler pertenece un viaje privado, pero rental sí dice qué bus, chofer y hora asignó. Se
 * cruzan por (bus, chofer, salida), que es única porque trip rechaza traslapes. Los dos eventos pueden llegar en
 * cualquier orden: el aviso sale cuando aparece el segundo, y solo cuando todos los tramos están programados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateTripServiceImpl implements PrivateTripService {

    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    private static final String EVENT_TYPE = "PrivateTripScheduled";
    private static final String TEMPLATE_CUSTOMER = "private-trip-customer";
    private static final String TEMPLATE_DRIVER = "private-trip-driver";

    private final PrivateTripLegRepository legRepository;
    private final TripDirectoryRepository tripRepository;
    private final DriverDirectoryRepository driverRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final RecipientResolver recipientResolver;
    private final EventMailer eventMailer;
    private final NotificationEventProperties eventProperties;
    private final Clock clock;

    @Override
    @Transactional
    public void recordAssignment(UUID eventId, Instant occurredAt, RentalAssignedEvent.Payload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }
        if (occurredAt == null || payload == null || payload.rentalId() == null || payload.branchId() == null
                || payload.busId() == null || payload.driverId() == null || payload.legs() == null
                || payload.legs().isEmpty()) {
            throw new IllegalArgumentException("RentalAssigned incompleto: " + eventId);
        }

        legRepository.deleteUnlinkedByRentalId(payload.rentalId());

        for (int index = 0; index < payload.legs().size(); index++) {
            RentalAssignedEvent.Leg leg = payload.legs().get(index);
            if (leg.departureAt() == null || leg.estimatedArrival() == null) {
                throw new IllegalArgumentException("Tramo incompleto en RentalAssigned: " + eventId);
            }
            PrivateTripLegEntity saved = legRepository.save(PrivateTripLegEntity.builder()
                    .rentalId(payload.rentalId())
                    .legNo(index + 1)
                    .branchId(payload.branchId())
                    .customerUserId(payload.customerUserId())
                    .customerNit(payload.customerNit())
                    .busId(payload.busId())
                    .driverId(payload.driverId())
                    .originText(leg.originText())
                    .destinationText(leg.destinationText())
                    .departureAt(toLocal(leg.departureAt()))
                    .estimatedArrival(toLocal(leg.estimatedArrival()))
                    .build());

            // trip pudo programar el viaje antes de que llegara esta asignación
            tripRepository.findUnlinkedPrivate(saved.getBusId(), saved.getDriverId(), saved.getDepartureAt())
                    .stream().findFirst().ifPresent(trip -> link(saved, trip));
        }

        notifyIfComplete(payload.rentalId(), occurredAt);
        processedEventRepository.save(ProcessedEventEntity.builder().eventId(eventId).build());
    }

    @Override
    @Transactional
    public void linkScheduledTrip(Instant occurredAt, TripDirectoryEntity trip) {
        legRepository.findUnlinked(trip.getBusId(), trip.getDriverId(), trip.getDepartureAt())
                .stream().findFirst().ifPresent(leg -> {
                    link(leg, trip);
                    notifyIfComplete(leg.getRentalId(), occurredAt);
                });
    }

    private void link(PrivateTripLegEntity leg, TripDirectoryEntity trip) {
        leg.setTripId(trip.getTripId());
        trip.setRentalId(leg.getRentalId());
        legRepository.save(leg);
        tripRepository.save(trip);
    }

    private void notifyIfComplete(UUID rentalId, Instant occurredAt) {
        List<PrivateTripLegEntity> legs = legRepository.findByRentalIdOrderByLegNoAsc(rentalId);
        if (legs.isEmpty() || legs.stream().anyMatch(leg -> leg.getTripId() == null || leg.getNotifiedAt() != null)) {
            return;
        }

        PrivateTripLegEntity first = legs.getFirst();
        StringBuilder key = new StringBuilder(rentalId.toString());
        legs.forEach(leg -> key.append('|').append(leg.getTripId()));
        UUID eventId = UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));

        String route = first.getOriginText() + " → " + first.getDestinationText();
        String customerSubject = "Tu viaje privado está programado: " + route;
        String driverSubject = "Nuevo viaje privado: " + route + ", " + EmailFormats.shortDateTime(first.getDepartureAt());

        if (isTooOld(occurredAt)) {
            String reason = "Evento de hace más de " + eventProperties.maxTripEventAge();
            eventMailer.send(new MailRequest(eventId, EVENT_TYPE, rentalId, TEMPLATE_CUSTOMER, customerSubject, null,
                    List.of(), reason));
            eventMailer.send(new MailRequest(eventId, EVENT_TYPE, rentalId, TEMPLATE_DRIVER, driverSubject, null,
                    List.of(), reason));
        } else {
            String plate = tripRepository.findById(first.getTripId()).map(TripDirectoryEntity::getBusPlate)
                    .filter(value -> value != null && !value.isBlank()).orElse("—");
            DriverDirectoryEntity driver = driverRepository.findById(first.getDriverId()).orElse(null);
            String driverName = driver == null ? "—" : driver.getFullName();

            List<NoticeRow> legRows = new ArrayList<>();
            for (int index = 0; index < legs.size(); index++) {
                PrivateTripLegEntity leg = legs.get(index);
                String label = legs.size() == 1 ? "Viaje" : index == 0 ? "Ida" : "Regreso";
                legRows.add(new NoticeRow(label, leg.getOriginText() + " → " + leg.getDestinationText()
                        + " · salida " + EmailFormats.shortDateTime(leg.getDepartureAt())
                        + ", llegada estimada " + EmailFormats.shortDateTime(leg.getEstimatedArrival())));
            }

            List<NoticeRow> customerRows = new ArrayList<>(legRows);
            customerRows.add(new NoticeRow("Bus", plate));
            customerRows.add(new NoticeRow("Chofer", driverName));
            NoticeView customerView = NoticeView.of(Tone.SUCCESS, "Tu viaje privado está programado",
                    "Hola, tu viaje privado quedó programado con el bus y el chofer asignados.", customerRows,
                    "Si necesitas cambiar algo, comunícate con la sucursal.");
            eventMailer.send(new MailRequest(eventId, EVENT_TYPE, rentalId, TEMPLATE_CUSTOMER, customerSubject,
                    customerView, recipientResolver.userEmail(first.getCustomerUserId()),
                    "El alquiler no tiene un cliente con correo en el directorio (venta en mostrador)"));

            List<NoticeRow> driverRows = new ArrayList<>(legRows);
            driverRows.add(new NoticeRow("Bus", plate));
            NoticeView driverView = NoticeView.of(Tone.INFO, "Se te asignó un viaje privado",
                    "Hola " + driverName + ", se te asignó un viaje privado.", driverRows,
                    "Presenta la salida y la llegada en el sistema al realizar el viaje.");
            eventMailer.send(new MailRequest(eventId, EVENT_TYPE, rentalId, TEMPLATE_DRIVER, driverSubject,
                    driverView, driver == null ? List.of() : List.of(driver.getEmail()),
                    "El chofer no está en el directorio"));
        }

        Instant now = Instant.now(clock);
        legs.forEach(leg -> leg.setNotifiedAt(now));
        legRepository.saveAll(legs);
    }

    private boolean isTooOld(Instant occurredAt) {
        Duration max = eventProperties.maxTripEventAge();
        return Duration.between(occurredAt, Instant.now(clock)).compareTo(max) > 0;
    }

    private LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, GUATEMALA).truncatedTo(ChronoUnit.MINUTES);
    }
}