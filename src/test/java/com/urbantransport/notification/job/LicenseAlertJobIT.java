package com.urbantransport.notification.job;

import com.urbantransport.notification.AbstractIntegrationTest;
import com.urbantransport.notification.domain.DriverDirectoryEntity;
import com.urbantransport.notification.domain.EmailLogEntity;
import com.urbantransport.notification.domain.EmailStatus;
import com.urbantransport.notification.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseAlertJobIT extends AbstractIntegrationTest {

    @Autowired
    private LicenseAlertJob licenseAlertJob;

    @Test
    void run_notifiesDriversInsideThresholdsAndTheirBranchAdmins() {
        UUID branchId = UUID.randomUUID();
        saveUser(Role.BRANCH_ADMIN, "admin@mail.com", branchId, null, true);
        saveUser(Role.BRANCH_ADMIN, "inactivo@mail.com", branchId, null, false);
        DriverDirectoryEntity urgent = saveDriver(branchId, "urgente@mail.com", today().plusDays(3), true);
        DriverDirectoryEntity soon = saveDriver(branchId, "pronto@mail.com", today().plusDays(10), true);
        saveDriver(branchId, "lejos@mail.com", today().plusDays(45), true);
        saveDriver(branchId, "inactivo-chofer@mail.com", today().plusDays(2), false);
        saveDriver(branchId, "vencido@mail.com", today().minusDays(1), true);

        licenseAlertJob.run();

        assertEquals(1, count("SELECT COUNT(*) FROM license_alert_sent WHERE driver_id = ? AND days_before = 3",
                urgent.getDriverId()));
        assertEquals(1, count("SELECT COUNT(*) FROM license_alert_sent WHERE driver_id = ? AND days_before = 15",
                soon.getDriverId()));
        assertEquals(2, count("SELECT COUNT(*) FROM license_alert_sent"));
        List<EmailLogEntity> emails = emailLogRepository.findAll();
        assertEquals(4, emails.size());
        assertTrue(emails.stream().allMatch(email -> email.getStatus() == EmailStatus.PENDING));
        assertEquals(List.of("admin@mail.com", "admin@mail.com"), emails.stream()
                .filter(email -> email.getTemplate().equals("license-expiring-admin"))
                .map(EmailLogEntity::getRecipient).toList());
        EmailLogEntity urgentMail = emails.stream()
                .filter(email -> "urgente@mail.com".equals(email.getRecipient())).findFirst().orElseThrow();
        assertEquals("license-expiring-driver", urgentMail.getTemplate());
        assertEquals("Tu licencia de conducir vence en 3 días", urgentMail.getSubject());
        assertEquals(urgent.getDriverId(), urgentMail.getReferenceId());
        assertEquals("LicenseExpiringCheck", urgentMail.getEventType());
    }

    @Test
    void secondRun_doesNotRepeatAlerts() {
        UUID branchId = UUID.randomUUID();
        saveUser(Role.BRANCH_ADMIN, "admin@mail.com", branchId, null, true);
        saveDriver(branchId, "urgente@mail.com", today().plusDays(3), true);

        licenseAlertJob.run();
        licenseAlertJob.run();

        assertEquals(1, count("SELECT COUNT(*) FROM license_alert_sent"));
        assertEquals(2, emailLogRepository.count());
    }

    @Test
    void branchWithoutAdmins_fallsBackToSystemAdminsOrIsSkipped() {
        UUID branchWithFallback = UUID.randomUUID();
        saveUser(Role.SYSTEM_ADMIN, "root@mail.com", null, null, true);
        saveDriver(branchWithFallback, "chofer@mail.com", today().plusDays(20), true);

        licenseAlertJob.run();

        EmailLogEntity adminMail = emailLogRepository.findAll().stream()
                .filter(email -> email.getTemplate().equals("license-expiring-admin")).findFirst().orElseThrow();
        assertEquals("root@mail.com", adminMail.getRecipient());
        assertEquals(EmailStatus.PENDING, adminMail.getStatus());
    }

    @Test
    void nobodyToNotify_recordsSkippedAdminMail() {
        saveDriver(UUID.randomUUID(), "chofer@mail.com", today(), true);

        licenseAlertJob.run();

        EmailLogEntity adminMail = emailLogRepository.findAll().stream()
                .filter(email -> email.getTemplate().equals("license-expiring-admin")).findFirst().orElseThrow();
        assertEquals(EmailStatus.SKIPPED, adminMail.getStatus());
        assertNull(adminMail.getRecipient());
        assertEquals("No hay administradores activos en el directorio", adminMail.getLastError());
        EmailLogEntity driverMail = emailLogRepository.findAll().stream()
                .filter(email -> email.getTemplate().equals("license-expiring-driver")).findFirst().orElseThrow();
        assertEquals("Tu licencia de conducir vence hoy", driverMail.getSubject());
    }
}
