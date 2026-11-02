package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationEventProperties;
import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.NoticeView;
import com.urbantransport.notification.dto.Tone;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.event.UserEvent;
import com.urbantransport.notification.service.EventMailer;
import com.urbantransport.notification.service.MailRequest;
import com.urbantransport.notification.service.WelcomeNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WelcomeNotificationServiceImpl implements WelcomeNotificationService {

    private static final String TEMPLATE = "welcome";
    private static final String SUBJECT = "Te damos la bienvenida a Code'n Bugs";

    private final EventMailer eventMailer;
    private final NotificationEventProperties eventProperties;
    private final Clock clock;

    @Override
    @Transactional
    public void handleUserCreated(UserEvent event) {
        UserEvent.UserPayload payload = event.payload();
        if (payload == null || payload.userId() == null || payload.email() == null || event.occurredAt() == null) {
            throw new IllegalArgumentException("UserCreated incompleto: " + event.eventId());
        }

        if (Duration.between(event.occurredAt(), Instant.now(clock)).compareTo(eventProperties.maxUserEventAge()) > 0) {
            skip(event, "Cuenta creada hace más de " + eventProperties.maxUserEventAge());
        } else if (!payload.active()) {
            skip(event, "La cuenta nació inactiva");
        } else {
            boolean customer = "CUSTOMER".equals(payload.role());
            String name = payload.fullName() == null ? "" : " " + payload.fullName();
            List<NoticeRow> rows = List.of(
                    new NoticeRow("Correo de acceso", payload.email()),
                    new NoticeRow("Tipo de cuenta", roleLabel(payload.role())));
            NoticeView view = customer
                    ? NoticeView.of(Tone.SUCCESS, "Te damos la bienvenida",
                    "Hola" + name + ", tu cuenta fue creada.", rows,
                    "Recarga tu cartera para comprar boletos y solicitar alquileres de buses.")
                    : NoticeView.of(Tone.INFO, "Tu cuenta fue creada",
                    "Hola" + name + ", se creó tu cuenta de acceso al sistema.", rows,
                    "Te enviamos otro correo con el enlace para crear tu contraseña.");
            eventMailer.send(new MailRequest(event.eventId(), EventTypes.USER_CREATED, payload.userId(), TEMPLATE,
                    SUBJECT, view, List.of(payload.email()), null));
        }
    }

    private void skip(UserEvent event, String reason) {
        eventMailer.send(new MailRequest(event.eventId(), EventTypes.USER_CREATED, event.payload().userId(), TEMPLATE,
                SUBJECT, null, List.of(), reason));
    }

    private String roleLabel(String role) {
        if (role == null) {
            return "Usuario";
        }
        return switch (role) {
            case "CUSTOMER" -> "Cliente";
            case "SYSTEM_ADMIN" -> "Administrador del sistema";
            case "BRANCH_ADMIN" -> "Administrador de sucursal";
            case "CASHIER" -> "Cajero";
            case "DRIVER" -> "Chofer";
            default -> role;
        };
    }
}