package com.urbantransport.notification.config;

import com.urbantransport.notification.service.EmailQueueService;
import com.urbantransport.notification.service.EmailRenderer;
import com.urbantransport.notification.service.EmailRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/** Prueba de humo: con notification.mail.smoke-test-to definido, encola un correo de prueba al arrancar. */
@Slf4j
@Component
@ConditionalOnExpression("!'${notification.mail.smoke-test-to:}'.isBlank()")
public class MailSmokeTestRunner implements ApplicationRunner {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final EmailRenderer renderer;
    private final EmailQueueService queue;
    private final NotificationMailProperties properties;
    private final TransactionTemplate transaction;

    public MailSmokeTestRunner(EmailRenderer renderer, EmailQueueService queue, NotificationMailProperties properties,
                               PlatformTransactionManager transactionManager) {
        this.renderer = renderer;
        this.queue = queue;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        String body = renderer.render("test", Map.of("sentAt", LocalDateTime.now().format(FORMAT)));
        transaction.executeWithoutResult(status -> queue.enqueue(new EmailRequest(
                UUID.randomUUID(), "SmokeTest", null, properties.smokeTestTo(),
                "test", "Prueba del servicio de notificaciones", body)));
        log.info("Correo de prueba encolado para {}", properties.smokeTestTo());
    }
}