package com.urbantransport.notification.config;

import com.urbantransport.notification.exception.EmailSendException;
import com.urbantransport.notification.service.EmailMessage;
import com.urbantransport.notification.service.EmailRenderer;
import com.urbantransport.notification.service.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Prueba de humo: con notification.mail.smoke-test-to definido, envía un correo de prueba al arrancar. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("!'${notification.mail.smoke-test-to:}'.isBlank()")
public class MailSmokeTestRunner implements ApplicationRunner {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final EmailRenderer renderer;
    private final EmailSender sender;
    private final NotificationMailProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        String body = renderer.render("test", Map.of("sentAt", LocalDateTime.now().format(FORMAT)));
        try {
            sender.send(new EmailMessage(properties.smokeTestTo(), "Prueba del servicio de notificaciones", body));
            log.info("Prueba de correo enviada a {}", properties.smokeTestTo());
        } catch (EmailSendException exception) {
            log.error("La prueba de correo falló (permanente={}): {}", exception.isPermanent(), exception.getMessage());
        }
    }
}