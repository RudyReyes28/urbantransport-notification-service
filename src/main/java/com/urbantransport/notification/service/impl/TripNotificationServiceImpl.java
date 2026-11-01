package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationEventProperties;
import com.urbantransport.notification.domain.DriverDirectoryEntity;
import com.urbantransport.notification.domain.PrivateTripLegEntity;
import com.urbantransport.notification.domain.ProcessedEventEntity;
import com.urbantransport.notification.domain.TripDirectoryEntity;
import com.urbantransport.notification.domain.TripKind;
import com.urbantransport.notification.domain.TripPassengerEntity;
import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.NoticeView;
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
import com.urbantransport.notification.service.TripNotificationService;
import com.urbantransport.notification.util.EmailFormats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripNotificationServiceImpl implements TripNotificationService {

    private static final String T_DRIVER_ASSIGNED = "trip-driver-assigned";
    private static final String T_DRIVER_UNASSIGNED = "trip-driver-unassigned";
    private static final String T_UPDATED_PASSENGER = "trip-updated-passenger";
    private static final String T_UPDATED_DRIVER = "trip-updated-driver";
    private static final String T_DELETED_DRIVER = "trip-deleted-driver";
    private static final String T_DELETED_CUSTOMER = "trip-deleted-customer";
    private static final String T_REJECTED_ADMIN = "private-trip-rejected-admin";

    private static final String NO_DRIVER = "El chofer no está en el directorio";

    /** Lo que importa comparar de un viaje antes y después de una modificación. */
    private record TripState(LocalDateTime departureAt, LocalDateTime estimatedArrival, String busPlate,
                             UUID driverId) {
    }

    private final ProcessedEventRepository processedEventRepository;
    private final TripDirectoryRepository tripRepository;
    private final TripPassengerRepository passengerRepository;
    private final PrivateTripLegRepository legRepository;
    private final DriverDirectoryRepository driverRepository;
    private final PrivateTripService privateTripService;
    private final RecipientResolver recipientResolver;
    private final EventMailer eventMailer;
    private final NotificationEventProperties eventProperties;
    private final Clock clock;

    // ---------- programado / modificado ----------

    @Override
    @Transactional
    public void handleTripUpsert(UUID eventId, String eventType, Instant occurredAt, TripUpsertEvent.Payload payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        if (occurredAt == null || payload == null || payload.tripId() == null || payload.departureAt() == null
                || payload.estimatedArrival() == null) {
            throw new IllegalArgumentException("Evento de viaje incompleto: " + eventId);
        }
        TripKind kind = parseKind(payload.type(), eventId);

        TripDirectoryEntity current = tripRepository.findById(payload.tripId()).orElse(null);
        if (current != null && !occurredAt.isAfter(current.getLastEventAt())) {
            log.debug("Evento {} del viaje {} es más viejo que el último aplicado, se descarta", eventId,
                    payload.tripId());
        } else {
            TripState before = current == null ? null : new TripState(current.getDepartureAt(),
                    current.getEstimatedArrival(), current.getBusPlate(), current.getDriverId());
            TripDirectoryEntity entity = current != null ? current : new TripDirectoryEntity();
            entity.setTripId(payload.tripId());
            entity.setType(kind);
            entity.setOriginName(firstNonBlank(payload.originName(), payload.originText()));
            entity.setDestinationName(firstNonBlank(payload.destinationName(), payload.destinationText()));
            entity.setDepartureAt(payload.departureAt().truncatedTo(ChronoUnit.MINUTES));
            entity.setEstimatedArrival(payload.estimatedArrival().truncatedTo(ChronoUnit.MINUTES));
            entity.setBusId(payload.busId());
            entity.setBusPlate(payload.busPlate());
            entity.setDriverId(payload.driverId());
            entity.setStatus(payload.status());
            entity.setLastEventAt(occurredAt);
            TripDirectoryEntity saved = tripRepository.save(entity);

            dispatch(eventId, eventType, occurredAt, kind, before, saved);
        }
        markProcessed(eventId);
    }

    private void dispatch(UUID eventId, String eventType, Instant occurredAt, TripKind kind, TripState before,
                          TripDirectoryEntity trip) {
        boolean scheduled = EventTypes.TRIP_SCHEDULED.equals(eventType);
        if (kind == TripKind.PRIVATE) {
            if (scheduled && trip.getRentalId() == null) {
                privateTripService.linkScheduledTrip(occurredAt, trip);
            }
            return;
        }
        boolean tooOld = isTooOld(occurredAt);
        if (scheduled) {
            notifyDriverAssigned(eventId, eventType, trip, tooOld);
        } else if (before == null) {
            log.debug("Viaje {} modificado sin estado previo guardado: no se puede decir qué cambió", trip.getTripId());
        } else {
            notifyChanges(eventId, eventType, before, trip, tooOld);
        }
    }

    private void notifyDriverAssigned(UUID eventId, String eventType, TripDirectoryEntity trip, boolean tooOld) {
        String subject = "Nuevo viaje asignado: " + route(trip) + ", " + EmailFormats.shortDateTime(trip.getDepartureAt());
        if (tooOld) {
            skipStale(eventId, eventType, trip.getTripId(), T_DRIVER_ASSIGNED, subject);
            return;
        }
        DriverDirectoryEntity driver = driverOf(trip.getDriverId());
        eventMailer.send(new MailRequest(eventId, eventType, trip.getTripId(), T_DRIVER_ASSIGNED, subject,
                driverAssignedView(driver, trip), driverEmails(driver), NO_DRIVER));
    }

    private NoticeView driverAssignedView(DriverDirectoryEntity driver, TripDirectoryEntity trip) {
        List<NoticeRow> rows = List.of(
                new NoticeRow("Viaje", route(trip)),
                new NoticeRow("Salida", EmailFormats.longDateTime(trip.getDepartureAt())),
                new NoticeRow("Llegada estimada", EmailFormats.longDateTime(trip.getEstimatedArrival())),
                new NoticeRow("Bus", plateOf(trip)));
        return NoticeView.of(Tone.INFO, "Se te asignó un viaje",
                "Hola" + (driver == null ? "" : " " + driver.getFullName()) + ", se te asignó un nuevo viaje.",
                rows, "Registra la salida y la llegada en el sistema al realizar el viaje.");
    }

    private void notifyChanges(UUID eventId, String eventType, TripState before, TripDirectoryEntity after,
                               boolean tooOld) {
        boolean departureChanged = !before.departureAt().equals(after.getDepartureAt());
        boolean arrivalChanged = !before.estimatedArrival().equals(after.getEstimatedArrival());
        boolean busChanged = !Objects.equals(before.busPlate(), after.getBusPlate());
        boolean driverChanged = !Objects.equals(before.driverId(), after.getDriverId());
        boolean tripChanged = departureChanged || arrivalChanged || busChanged;
        if (!tripChanged && !driverChanged) {
            log.debug("Viaje {} modificado sin cambios que avisar", after.getTripId());
            return;
        }

        String subject = "Cambió tu viaje: " + route(after);
        if (tooOld) {
            skipStale(eventId, eventType, after.getTripId(), T_UPDATED_PASSENGER, subject);
            return;
        }

        List<NoticeRow> changes = new ArrayList<>();
        changes.add(new NoticeRow("Viaje", route(after)));
        if (departureChanged) {
            changes.add(new NoticeRow("Salida", EmailFormats.shortDateTime(before.departureAt()) + " → "
                    + EmailFormats.shortDateTime(after.getDepartureAt())));
        }
        if (arrivalChanged) {
            changes.add(new NoticeRow("Llegada estimada", EmailFormats.shortDateTime(before.estimatedArrival())
                    + " → " + EmailFormats.shortDateTime(after.getEstimatedArrival())));
        }
        if (busChanged) {
            changes.add(new NoticeRow("Bus", (before.busPlate() == null ? "—" : before.busPlate()) + " → "
                    + plateOf(after)));
        }

        if (tripChanged) {
            notifyPassengers(eventId, eventType, after, changes, subject);
        }

        if (driverChanged) {
            DriverDirectoryEntity newDriver = driverOf(after.getDriverId());
            eventMailer.send(new MailRequest(eventId, eventType, after.getTripId(), T_DRIVER_ASSIGNED,
                    "Nuevo viaje asignado: " + route(after) + ", " + EmailFormats.shortDateTime(after.getDepartureAt()),
                    driverAssignedView(newDriver, after), driverEmails(newDriver), NO_DRIVER));

            DriverDirectoryEntity oldDriver = driverOf(before.driverId());
            NoticeView unassignedView = NoticeView.of(Tone.WARNING, "Ya no estás asignado a este viaje",
                    "Hola" + (oldDriver == null ? "" : " " + oldDriver.getFullName())
                            + ", la sucursal asignó otro chofer a este viaje.",
                    List.of(new NoticeRow("Viaje", route(after)),
                            new NoticeRow("Salida", EmailFormats.longDateTime(after.getDepartureAt()))),
                    null);
            eventMailer.send(new MailRequest(eventId, eventType, after.getTripId(), T_DRIVER_UNASSIGNED,
                    "Ya no estás asignado al viaje " + route(after), unassignedView, driverEmails(oldDriver),
                    NO_DRIVER));
        } else {
            DriverDirectoryEntity driver = driverOf(after.getDriverId());
            NoticeView view = NoticeView.of(Tone.WARNING, "Tu viaje cambió",
                    "Hola" + (driver == null ? "" : " " + driver.getFullName()) + ", se modificó un viaje que tienes asignado.",
                    changes, "Revisa los nuevos datos antes de la salida.");
            eventMailer.send(new MailRequest(eventId, eventType, after.getTripId(), T_UPDATED_DRIVER,
                    "Cambió tu viaje: " + route(after), view, driverEmails(driver), NO_DRIVER));
        }
    }

    /** Un correo por comprador, con todos sus asientos del viaje. */
    private void notifyPassengers(UUID eventId, String eventType, TripDirectoryEntity trip, List<NoticeRow> changes,
                                  String subject) {
        Map<String, List<String>> seatsByBuyer = new LinkedHashMap<>();
        for (TripPassengerEntity passenger : passengerRepository.findByTripId(trip.getTripId())) {
            seatsByBuyer.computeIfAbsent(passenger.getEmail(), email -> new ArrayList<>())
                    .add(passenger.getSeatLabel());
        }
        seatsByBuyer.forEach((email, seats) -> {
            List<NoticeRow> rows = new ArrayList<>(changes);
            rows.add(new NoticeRow("Tus asientos", String.join(", ", seats)));
            NoticeView view = NoticeView.of(Tone.WARNING, "Cambió tu viaje",
                    "Hola, se modificó un viaje en el que tienes boletos. Estos son los nuevos datos.", rows,
                    "Tus boletos siguen vigentes. Si no puedes viajar en el nuevo horario, comunícate con la sucursal.");
            eventMailer.send(new MailRequest(eventId, eventType, trip.getTripId(), T_UPDATED_PASSENGER, subject,
                    view, List.of(email), null));
        });
    }

    // ---------- eliminado ----------

    @Override
    @Transactional
    public void handleTripDeleted(UUID eventId, Instant occurredAt, TripDeletedEvent.Payload payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        if (occurredAt == null || payload == null || payload.tripId() == null) {
            throw new IllegalArgumentException("TripDeleted incompleto: " + eventId);
        }

        TripDirectoryEntity trip = tripRepository.findById(payload.tripId()).orElse(null);
        if (trip != null) {
            trip.setStatus("DELETED");
            tripRepository.save(trip);
        }
        String subject = trip == null
                ? "Viaje cancelado"
                : "Viaje cancelado: " + route(trip) + ", " + EmailFormats.shortDateTime(trip.getDepartureAt());

        if (isTooOld(occurredAt)) {
            skipStale(eventId, EventTypes.TRIP_DELETED, payload.tripId(), T_DELETED_DRIVER, subject);
        } else {
            List<NoticeRow> rows = trip == null ? List.of() : List.of(
                    new NoticeRow("Viaje", route(trip)),
                    new NoticeRow("Salida", EmailFormats.longDateTime(trip.getDepartureAt())));

            UUID driverId = payload.driverId() != null ? payload.driverId() : trip == null ? null : trip.getDriverId();
            DriverDirectoryEntity driver = driverOf(driverId);
            NoticeView driverView = NoticeView.of(Tone.DANGER, "Se canceló un viaje que tenías asignado",
                    "Hola" + (driver == null ? "" : " " + driver.getFullName())
                            + ", la sucursal canceló un viaje que tenías asignado.", rows, null);
            eventMailer.send(new MailRequest(eventId, EventTypes.TRIP_DELETED, payload.tripId(), T_DELETED_DRIVER,
                    subject, driverView, driverEmails(driver), NO_DRIVER));

            if (payload.rentalId() != null) {
                notifyCustomerOfDeletedPrivateTrip(eventId, payload, rows);
            }
        }
        markProcessed(eventId);
    }

    private void notifyCustomerOfDeletedPrivateTrip(UUID eventId, TripDeletedEvent.Payload payload,
                                                    List<NoticeRow> rows) {
        List<PrivateTripLegEntity> legs = legRepository.findByRentalIdOrderByLegNoAsc(payload.rentalId());
        UUID customerUserId = legs.isEmpty() ? null : legs.getFirst().getCustomerUserId();
        NoticeView view = NoticeView.of(Tone.DANGER, "Tu viaje privado fue cancelado",
                "Hola, la sucursal canceló tu viaje privado.", rows,
                "Comunícate con la sucursal para coordinar una nueva fecha o el reembolso.");
        eventMailer.send(new MailRequest(eventId, EventTypes.TRIP_DELETED, payload.tripId(), T_DELETED_CUSTOMER,
                "Tu viaje privado fue cancelado", view, recipientResolver.userEmail(customerUserId),
                "El alquiler no tiene un cliente con correo en el directorio"));
    }

    // ---------- viaje privado rechazado ----------

    @Override
    @Transactional
    public void handlePrivateTripRejected(UUID eventId, Instant occurredAt, PrivateTripRejectedEvent.Payload payload) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        if (occurredAt == null || payload == null || payload.rentalId() == null) {
            throw new IllegalArgumentException("PrivateTripRejected incompleto: " + eventId);
        }
        List<PrivateTripLegEntity> legs = legRepository.findByRentalIdOrderByLegNoAsc(payload.rentalId());
        if (legs.isEmpty()) {
            // la asignación de rental aún no se procesó: se reintenta unos segundos antes de ir al DLT
            throw new IllegalStateException("Rechazo del alquiler " + payload.rentalId()
                    + " antes de conocer su asignación");
        }

        PrivateTripLegEntity first = legs.getFirst();
        String route = first.getOriginText() + " → " + first.getDestinationText();
        String subject = "Viaje privado rechazado: " + route;

        if (isTooOld(occurredAt)) {
            skipStale(eventId, EventTypes.PRIVATE_TRIP_REJECTED, payload.rentalId(), T_REJECTED_ADMIN, subject);
        } else {
            DriverDirectoryEntity driver = driverOf(payload.driverId());
            List<NoticeRow> rows = List.of(
                    new NoticeRow("Motivo", payload.reason() == null ? "—" : payload.reason()),
                    new NoticeRow("Ruta", route),
                    new NoticeRow("Salida", EmailFormats.shortDateTime(first.getDepartureAt())),
                    new NoticeRow("Chofer", driver == null ? "—" : driver.getFullName()));
            NoticeView view = NoticeView.of(Tone.DANGER, "No se pudo programar el viaje privado",
                    "Trip rechazó la asignación de bus y chofer de un alquiler.", rows,
                    "El alquiler volvió a estado pagado: asigna otro bus u otro chofer, o reprograma la fecha.");
            eventMailer.send(new MailRequest(eventId, EventTypes.PRIVATE_TRIP_REJECTED, payload.rentalId(),
                    T_REJECTED_ADMIN, subject, view, recipientResolver.branchAdmins(first.getBranchId()),
                    "No hay administradores activos en el directorio"));
        }

        legRepository.deleteUnlinkedByRentalId(payload.rentalId());
        markProcessed(eventId);
    }

    // ---------- utilidades ----------

    private DriverDirectoryEntity driverOf(UUID driverId) {
        return driverId == null ? null : driverRepository.findById(driverId).orElse(null);
    }

    private List<String> driverEmails(DriverDirectoryEntity driver) {
        return driver == null ? List.of() : List.of(driver.getEmail());
    }

    private String route(TripDirectoryEntity trip) {
        return trip.getOriginName() + " → " + trip.getDestinationName();
    }

    private String plateOf(TripDirectoryEntity trip) {
        return trip.getBusPlate() == null || trip.getBusPlate().isBlank() ? "—" : trip.getBusPlate();
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private TripKind parseKind(String value, UUID eventId) {
        try {
            return TripKind.valueOf(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Tipo de viaje inválido en " + eventId + ": " + value);
        }
    }

    private void skipStale(UUID eventId, String eventType, UUID referenceId, String template, String subject) {
        eventMailer.send(new MailRequest(eventId, eventType, referenceId, template, subject, null, List.of(),
                "Evento de hace más de " + eventProperties.maxTripEventAge()));
    }

    private boolean isTooOld(Instant occurredAt) {
        Duration max = eventProperties.maxTripEventAge();
        return Duration.between(occurredAt, Instant.now(clock)).compareTo(max) > 0;
    }

    private boolean alreadyProcessed(UUID eventId) {
        boolean processed = processedEventRepository.existsById(eventId);
        if (processed) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
        }
        return processed;
    }

    private void markProcessed(UUID eventId) {
        processedEventRepository.save(ProcessedEventEntity.builder().eventId(eventId).build());
    }
}