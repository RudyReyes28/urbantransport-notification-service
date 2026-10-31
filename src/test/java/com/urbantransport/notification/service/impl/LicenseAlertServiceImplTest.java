package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationLicenseProperties;
import com.urbantransport.notification.domain.DriverDirectoryEntity;
import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.Tone;
import com.urbantransport.notification.repository.DriverDirectoryRepository;
import com.urbantransport.notification.service.EventMailer;
import com.urbantransport.notification.service.MailRequest;
import com.urbantransport.notification.service.RecipientResolver;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LicenseAlertServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.now(CLOCK.withZone(ZONE));
    private static final UUID DRIVER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_DRIVER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BRANCH_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final List<String> ADMINS = List.of("admin@mail.com");

    @Mock
    private DriverDirectoryRepository driverDirectoryRepository;

    @Mock
    private RecipientResolver recipientResolver;

    @Mock
    private EventMailer eventMailer;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Captor
    private ArgumentCaptor<MailRequest> mailCaptor;

    @Nested
    class RunCheck {

        @ParameterizedTest
        @CsvSource({"0, 3, vence hoy, DANGER", "1, 3, 'vence mañana', DANGER", "3, 3, vence en 3 días, DANGER",
                "10, 15, vence en 10 días, WARNING", "15, 15, vence en 15 días, WARNING",
                "20, 30, vence en 20 días, INFO", "30, 30, vence en 30 días, INFO"})
        void driverInsideAThreshold_isClaimedAndBothMailsAreSent(int daysLeft, int threshold, String when, Tone tone) {
            DriverDirectoryEntity driver = driver(DRIVER_ID, TODAY.plusDays(daysLeft));
            when(driverDirectoryRepository.findByActiveTrueAndLicenseExpiresOnBetween(TODAY, TODAY.plusDays(30)))
                    .thenReturn(List.of(driver));
            runTransactions();
            when(driverDirectoryRepository.claimAlert(DRIVER_ID, driver.getLicenseExpiresOn(), threshold))
                    .thenReturn(1);
            when(recipientResolver.branchAdmins(BRANCH_ID)).thenReturn(ADMINS);

            int notified = service(List.of(15, 3, 30)).runCheck();

            assertEquals(1, notified);
            verify(eventMailer, times(2)).send(mailCaptor.capture());
            UUID eventId = UUID.nameUUIDFromBytes((DRIVER_ID + "|" + driver.getLicenseExpiresOn() + "|" + threshold)
                    .getBytes(StandardCharsets.UTF_8));
            MailRequest driverMail = mailCaptor.getAllValues().getFirst();
            assertEquals(eventId, driverMail.eventId());
            assertEquals("LicenseExpiringCheck", driverMail.eventType());
            assertEquals(DRIVER_ID, driverMail.referenceId());
            assertEquals("license-expiring-driver", driverMail.template());
            assertEquals("Tu licencia de conducir " + when, driverMail.subject());
            assertEquals(List.of("juan@mail.com"), List.copyOf(driverMail.recipients()));
            assertEquals(tone, driverMail.view().getTone());
            assertEquals("Tu licencia " + when, driverMail.view().getHeading());
            assertEquals(String.valueOf(daysLeft), rowValue(driverMail, "Días restantes"));
            assertEquals("LIC-001", rowValue(driverMail, "Licencia"));
            assertNull(driverMail.skipReason());
            MailRequest adminMail = mailCaptor.getAllValues().getLast();
            assertEquals(eventId, adminMail.eventId());
            assertEquals("license-expiring-admin", adminMail.template());
            assertEquals("Licencia de Juan Perez " + when, adminMail.subject());
            assertEquals(ADMINS, adminMail.recipients());
            assertEquals("No hay administradores activos en el directorio", adminMail.skipReason());
        }

        @Test
        void alreadyClaimedThreshold_sendsNothing() {
            DriverDirectoryEntity driver = driver(DRIVER_ID, TODAY.plusDays(3));
            when(driverDirectoryRepository.findByActiveTrueAndLicenseExpiresOnBetween(TODAY, TODAY.plusDays(30)))
                    .thenReturn(List.of(driver));
            runTransactions();
            when(driverDirectoryRepository.claimAlert(DRIVER_ID, driver.getLicenseExpiresOn(), 3)).thenReturn(0);

            assertEquals(0, service(List.of(30, 15, 3)).runCheck());

            verifyNoInteractions(eventMailer, recipientResolver);
        }

        @Test
        void driverBeyondLastThreshold_isSkippedWithoutClaim() {
            when(driverDirectoryRepository.findByActiveTrueAndLicenseExpiresOnBetween(TODAY, TODAY.plusDays(30)))
                    .thenReturn(List.of(driver(DRIVER_ID, TODAY.plusDays(45))));

            assertEquals(0, service(List.of(30, 15, 3)).runCheck());

            verify(driverDirectoryRepository, never()).claimAlert(any(), any(), anyInt());
            verifyNoInteractions(transactionTemplate, eventMailer);
        }

        @Test
        void failingDriver_doesNotStopTheOthers() {
            DriverDirectoryEntity failing = driver(DRIVER_ID, TODAY.plusDays(3));
            DriverDirectoryEntity ok = driver(OTHER_DRIVER_ID, TODAY.plusDays(10));
            when(driverDirectoryRepository.findByActiveTrueAndLicenseExpiresOnBetween(TODAY, TODAY.plusDays(30)))
                    .thenReturn(List.of(failing, ok));
            runTransactions();
            when(driverDirectoryRepository.claimAlert(DRIVER_ID, failing.getLicenseExpiresOn(), 3)).thenReturn(1);
            when(driverDirectoryRepository.claimAlert(OTHER_DRIVER_ID, ok.getLicenseExpiresOn(), 15)).thenReturn(1);
            when(recipientResolver.branchAdmins(BRANCH_ID)).thenReturn(ADMINS);
            doThrow(new IllegalStateException("BD caida")).doNothing().when(eventMailer).send(any());

            assertEquals(1, service(List.of(30, 15, 3)).runCheck());

            verify(eventMailer, times(3)).send(any());
        }

        @Test
        void noCandidates_returnsZero() {
            when(driverDirectoryRepository.findByActiveTrueAndLicenseExpiresOnBetween(TODAY, TODAY.plusDays(15)))
                    .thenReturn(List.of());

            assertEquals(0, service(List.of(15)).runCheck());

            verifyNoInteractions(transactionTemplate, eventMailer);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void withoutThresholds_checksNothing(List<Integer> alertDays) {
            assertEquals(0, service(alertDays).runCheck());

            verifyNoInteractions(driverDirectoryRepository, transactionTemplate, eventMailer, recipientResolver);
        }
    }

    private LicenseAlertServiceImpl service(List<Integer> alertDays) {
        return new LicenseAlertServiceImpl(driverDirectoryRepository, recipientResolver, eventMailer,
                new NotificationLicenseProperties(alertDays, false), transactionTemplate, CLOCK);
    }

    private void runTransactions() {
        when(transactionTemplate.execute(any()))
                .thenAnswer(inv -> inv.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    private static String rowValue(MailRequest request, String label) {
        return request.view().getRows().stream().filter(row -> row.getLabel().equals(label))
                .map(NoticeRow::getValue).findFirst().orElse(null);
    }

    private static DriverDirectoryEntity driver(UUID driverId, LocalDate expiresOn) {
        return DriverDirectoryEntity.builder()
                .driverId(driverId)
                .fullName("Juan Perez")
                .email("juan@mail.com")
                .branchId(BRANCH_ID)
                .licenseNumber("LIC-001")
                .licenseExpiresOn(expiresOn)
                .active(true)
                .lastEventAt(NOW)
                .build();
    }
}
