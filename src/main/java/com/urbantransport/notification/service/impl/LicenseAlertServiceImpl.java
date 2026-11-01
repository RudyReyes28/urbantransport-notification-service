package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationLicenseProperties;
import com.urbantransport.notification.domain.DriverDirectoryEntity;
import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.NoticeView;
import com.urbantransport.notification.dto.Tone;
import com.urbantransport.notification.repository.DriverDirectoryRepository;
import com.urbantransport.notification.service.EventMailer;
import com.urbantransport.notification.service.LicenseAlertService;
import com.urbantransport.notification.service.MailRequest;
import com.urbantransport.notification.service.RecipientResolver;
import com.urbantransport.notification.util.EmailFormats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseAlertServiceImpl implements LicenseAlertService {

    private static final String EVENT_TYPE = "LicenseExpiringCheck";
    private static final String TEMPLATE_DRIVER = "license-expiring-driver";
    private static final String TEMPLATE_ADMIN = "license-expiring-admin";
    private static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");

    private final DriverDirectoryRepository driverDirectoryRepository;
    private final RecipientResolver recipientResolver;
    private final EventMailer eventMailer;
    private final NotificationLicenseProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    public int runCheck() {
        List<Integer> thresholds = properties.alertDays() == null ? List.of()
                : properties.alertDays().stream().sorted().toList();
        if (thresholds.isEmpty()) {
            log.warn("notification.license.alert-days está vacío: no se revisan licencias");
            return 0;
        }

        LocalDate today = LocalDate.now(clock.withZone(GUATEMALA));
        List<DriverDirectoryEntity> candidates = driverDirectoryRepository
                .findByActiveTrueAndLicenseExpiresOnBetween(today, today.plusDays(thresholds.getLast()));

        int notified = 0;
        for (DriverDirectoryEntity driver : candidates) {
            long daysLeft = ChronoUnit.DAYS.between(today, driver.getLicenseExpiresOn());
            Integer threshold = thresholds.stream().filter(limit -> daysLeft <= limit).findFirst().orElse(null);
            if (threshold == null) {
                continue;
            }
            try {
                Boolean sent = transactionTemplate.execute(status -> notifyDriver(driver, threshold, daysLeft));
                if (Boolean.TRUE.equals(sent)) {
                    notified++;
                }
            } catch (RuntimeException exception) {
                log.error("No se pudo avisar de la licencia del chofer {}; se reintenta en la siguiente corrida",
                        driver.getDriverId(), exception);
            }
        }
        return notified;
    }

    /** El reclamo y los correos encolados van en la misma transacción: si algo falla, el aviso sigue pendiente. */
    private boolean notifyDriver(DriverDirectoryEntity driver, int threshold, long daysLeft) {
        if (driverDirectoryRepository.claimAlert(driver.getDriverId(), driver.getLicenseExpiresOn(), threshold) == 0) {
            return false;
        }

        UUID eventId = UUID.nameUUIDFromBytes(
                (driver.getDriverId() + "|" + driver.getLicenseExpiresOn() + "|" + threshold)
                        .getBytes(StandardCharsets.UTF_8));
        String when = when(daysLeft);
        List<NoticeRow> rows = List.of(
                new NoticeRow("Chofer", driver.getFullName()),
                new NoticeRow("Licencia", driver.getLicenseNumber()),
                new NoticeRow("Vence el", EmailFormats.date(driver.getLicenseExpiresOn())),
                new NoticeRow("Días restantes", String.valueOf(daysLeft)));
        Tone tone = daysLeft <= 3 ? Tone.DANGER : daysLeft <= 15 ? Tone.WARNING : Tone.INFO;

        NoticeView driverView = NoticeView.of(tone, "Tu licencia " + when,
                "Hola " + driver.getFullName() + ", tu licencia de conducir " + when + ".", rows,
                "Renuévala a tiempo: con la licencia vencida no podrás ser asignado a viajes.");
        eventMailer.send(new MailRequest(eventId, EVENT_TYPE, driver.getDriverId(), TEMPLATE_DRIVER,
                "Tu licencia de conducir " + when, driverView, List.of(driver.getEmail()), null));

        NoticeView adminView = NoticeView.of(tone, "Licencia de " + driver.getFullName() + " " + when,
                "La licencia del chofer " + driver.getFullName() + " de tu sucursal " + when + ".", rows,
                "Coordina la renovación. No se pueden programar viajes con una licencia que venza antes de la fecha del viaje.");
        eventMailer.send(new MailRequest(eventId, EVENT_TYPE, driver.getDriverId(), TEMPLATE_ADMIN,
                "Licencia de " + driver.getFullName() + " " + when, adminView,
                recipientResolver.branchAdmins(driver.getBranchId()),
                "No hay administradores activos en el directorio"));
        return true;
    }

    private String when(long daysLeft) {
        if (daysLeft == 0) {
            return "vence hoy";
        }
        return daysLeft == 1 ? "vence mañana" : "vence en " + daysLeft + " días";
    }
}