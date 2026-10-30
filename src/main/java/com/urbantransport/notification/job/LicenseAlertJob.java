package com.urbantransport.notification.job;

import com.urbantransport.notification.config.NotificationLicenseProperties;
import com.urbantransport.notification.service.LicenseAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.license.enabled", havingValue = "true", matchIfMissing = true)
public class LicenseAlertJob {

    private final LicenseAlertService licenseAlertService;
    private final NotificationLicenseProperties properties;

    @Scheduled(cron = "${notification.license.cron:0 0 7 * * *}", zone = "America/Guatemala")
    public void run() {
        execute("programada");
    }

    /** Opcional: al arrancar revisa lo atrasado (útil si el servicio estuvo apagado a la hora del job). */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (properties.runOnStartup()) {
            execute("de arranque");
        }
    }

    private void execute(String origin) {
        try {
            int notified = licenseAlertService.runCheck();
            log.info("Revisión {} de licencias: {} choferes avisados", origin, notified);
        } catch (RuntimeException exception) {
            log.error("Falló la revisión {} de licencias", origin, exception);
        }
    }
}