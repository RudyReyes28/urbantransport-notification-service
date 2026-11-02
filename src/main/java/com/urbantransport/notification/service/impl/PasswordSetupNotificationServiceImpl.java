package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.NoticeView;
import com.urbantransport.notification.dto.Tone;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.event.PasswordSetupRequestedEvent;
import com.urbantransport.notification.service.EventMailer;
import com.urbantransport.notification.service.MailRequest;
import com.urbantransport.notification.service.PasswordSetupNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PasswordSetupNotificationServiceImpl implements PasswordSetupNotificationService {

    private static final String TEMPLATE = "password-setup";
    private static final String RESEND_REQUESTED = "RESEND_REQUESTED";
    private static final String CREATED_SUBJECT = "Crea tu contraseña de Code'n Bugs";
    private static final String RESEND_SUBJECT = "Tu nuevo enlace para crear tu contraseña de Code'n Bugs";
    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final DateTimeFormatter EXPIRES_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EventMailer eventMailer;
    private final Clock clock;

    @Override
    @Transactional
    public void handlePasswordSetupRequested(PasswordSetupRequestedEvent event) {
        PasswordSetupRequestedEvent.Payload payload = event.payload();
        if (payload == null || payload.userId() == null || payload.email() == null || payload.setupUrl() == null
                || payload.expiresAt() == null) {
            throw new IllegalArgumentException("PasswordSetupRequested incompleto: " + event.eventId());
        }

        boolean resend = RESEND_REQUESTED.equals(payload.purpose());
        String subject = resend ? RESEND_SUBJECT : CREATED_SUBJECT;
        if (!payload.expiresAt().isAfter(Instant.now(clock))) {
            eventMailer.send(new MailRequest(event.eventId(), EventTypes.PASSWORD_SETUP_REQUESTED, payload.userId(),
                    TEMPLATE, subject, null, List.of(), "El enlace ya venció"));
            return;
        }

        String name = payload.fullName() == null ? "" : " " + payload.fullName();
        String intro = resend
                ? "Hola" + name + ", recibimos una solicitud para crear o cambiar tu contraseña. Usa el siguiente botón."
                : "Hola" + name + ", se creó tu cuenta. Para ingresar, crea tu contraseña con el siguiente botón.";
        String note = resend
                ? "El enlace solo funciona una vez y reemplaza a los anteriores. Si no lo solicitaste, ignora este correo."
                : "El enlace solo funciona una vez. Si no solicitaste esta cuenta, ignora este correo.";
        NoticeView view = NoticeView.withAction(Tone.INFO, "Crea tu contraseña", intro,
                List.of(new NoticeRow("Correo de acceso", payload.email()),
                        new NoticeRow("Enlace válido hasta", EXPIRES_FORMAT.format(payload.expiresAt().atZone(ZONE)))),
                note, "Crear contraseña", payload.setupUrl());
        eventMailer.send(new MailRequest(event.eventId(), EventTypes.PASSWORD_SETUP_REQUESTED, payload.userId(),
                TEMPLATE, subject, view, List.of(payload.email()), null));
    }
}
