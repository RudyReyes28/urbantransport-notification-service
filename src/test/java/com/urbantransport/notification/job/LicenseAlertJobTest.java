package com.urbantransport.notification.job;

import com.urbantransport.notification.config.NotificationLicenseProperties;
import com.urbantransport.notification.service.LicenseAlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LicenseAlertJobTest {

    @Mock
    private LicenseAlertService licenseAlertService;

    @Test
    void scheduledRun_runsTheCheck() {
        when(licenseAlertService.runCheck()).thenReturn(2);

        job(false).run();

        verify(licenseAlertService).runCheck();
    }

    @Test
    void failingCheck_isLoggedAndNotRethrown() {
        when(licenseAlertService.runCheck()).thenThrow(new IllegalStateException("BD caida"));

        assertDoesNotThrow(() -> job(false).run());

        verify(licenseAlertService).runCheck();
    }

    @Test
    void startupWithRunOnStartup_runsTheCheck() {
        when(licenseAlertService.runCheck()).thenReturn(0);

        job(true).onStartup();

        verify(licenseAlertService).runCheck();
    }

    @Test
    void startupWithoutRunOnStartup_doesNothing() {
        job(false).onStartup();

        verifyNoInteractions(licenseAlertService);
    }

    private LicenseAlertJob job(boolean runOnStartup) {
        return new LicenseAlertJob(licenseAlertService, new NotificationLicenseProperties(List.of(30, 15, 3),
                runOnStartup));
    }
}
