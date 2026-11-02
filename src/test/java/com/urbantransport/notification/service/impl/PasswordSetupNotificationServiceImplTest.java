package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.Tone;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.event.PasswordSetupRequestedEvent;
import com.urbantransport.notification.service.EventMailer;
import com.urbantransport.notification.service.MailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PasswordSetupNotificationServiceImplTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String SETUP_URL = "http://localhost:4200/set-password?token=abc123";
    private static final Instant EXPIRES_AT = NOW.plus(48, ChronoUnit.HOURS);

    @Mock
    private EventMailer eventMailer;

    @Captor
    private ArgumentCaptor<MailRequest> mailCaptor;

    private PasswordSetupNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PasswordSetupNotificationServiceImpl(eventMailer, CLOCK);
    }

    @Nested
    class HandlePasswordSetupRequested {

        @Test
        void validLink_sendsEmailWithTheButton() {
            service.handlePasswordSetupRequested(event(payload("Juan Perez", EXPIRES_AT)));

            verify(eventMailer).send(mailCaptor.capture());
            MailRequest mail = mailCaptor.getValue();
            assertEquals(EVENT_ID, mail.eventId());
            assertEquals(EventTypes.PASSWORD_SETUP_REQUESTED, mail.eventType());
            assertEquals(USER_ID, mail.referenceId());
            assertEquals("password-setup", mail.template());
            assertEquals("Crea tu contraseña de Code'n Bugs", mail.subject());
            assertEquals(List.of("juan@mail.com"), mail.recipients());
            assertNull(mail.skipReason());
            assertEquals(Tone.INFO, mail.view().getTone());
            assertEquals("Crear contraseña", mail.view().getActionLabel());
            assertEquals(SETUP_URL, mail.view().getActionUrl());
            assertTrue(mail.view().getIntro().startsWith("Hola Juan Perez, se creó tu cuenta."));
            assertEquals("juan@mail.com", rowValue(mail, "Correo de acceso"));
            assertEquals(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                            .format(EXPIRES_AT.atZone(ZoneId.of("America/Guatemala"))),
                    rowValue(mail, "Enlace válido hasta"));
        }

        @Test
        void resendRequested_usesResendSubjectAndText() {
            service.handlePasswordSetupRequested(event(payload("Juan", EXPIRES_AT, "RESEND_REQUESTED")));

            verify(eventMailer).send(mailCaptor.capture());
            MailRequest mail = mailCaptor.getValue();
            assertEquals("Tu nuevo enlace para crear tu contraseña de Code'n Bugs", mail.subject());
            assertTrue(mail.view().getIntro().startsWith("Hola Juan, recibimos una solicitud"));
            assertTrue(mail.view().getNote().contains("reemplaza a los anteriores"));
            assertEquals(SETUP_URL, mail.view().getActionUrl());
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"ACCOUNT_CREATED", "OTRO"})
        void otherOrMissingPurpose_usesAccountCreatedText(String purpose) {
            service.handlePasswordSetupRequested(event(payload("Juan", EXPIRES_AT, purpose)));

            verify(eventMailer).send(mailCaptor.capture());
            assertEquals("Crea tu contraseña de Code'n Bugs", mailCaptor.getValue().subject());
            assertTrue(mailCaptor.getValue().view().getIntro().startsWith("Hola Juan, se creó tu cuenta."));
        }

        @Test
        void userWithoutName_isGreetedWithoutName() {
            service.handlePasswordSetupRequested(event(payload(null, EXPIRES_AT)));

            verify(eventMailer).send(mailCaptor.capture());
            assertTrue(mailCaptor.getValue().view().getIntro().startsWith("Hola, se creó tu cuenta."));
        }

        @ParameterizedTest
        @ValueSource(longs = {0, 60, 86400})
        void expiredLink_isSkipped(long secondsAgo) {
            service.handlePasswordSetupRequested(event(payload("Juan", NOW.minusSeconds(secondsAgo))));

            verify(eventMailer).send(mailCaptor.capture());
            assertTrue(mailCaptor.getValue().recipients().isEmpty());
            assertNull(mailCaptor.getValue().view());
            assertEquals("El enlace ya venció", mailCaptor.getValue().skipReason());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.PasswordSetupNotificationServiceImplTest#incomplete")
        void incompleteEvent_throwsAndSendsNothing(PasswordSetupRequestedEvent event) {
            assertThrows(IllegalArgumentException.class, () -> service.handlePasswordSetupRequested(event));

            verifyNoInteractions(eventMailer);
        }
    }

    static Stream<PasswordSetupRequestedEvent> incomplete() {
        return Stream.of(
                event(null),
                event(new PasswordSetupRequestedEvent.Payload(null, "juan@mail.com", "Juan", "DRIVER", "ACCOUNT_CREATED", SETUP_URL, EXPIRES_AT)),
                event(new PasswordSetupRequestedEvent.Payload(USER_ID, null, "Juan", "DRIVER", "ACCOUNT_CREATED", SETUP_URL, EXPIRES_AT)),
                event(new PasswordSetupRequestedEvent.Payload(USER_ID, "juan@mail.com", "Juan", "DRIVER", "ACCOUNT_CREATED", null, EXPIRES_AT)),
                event(new PasswordSetupRequestedEvent.Payload(USER_ID, "juan@mail.com", "Juan", "DRIVER", "ACCOUNT_CREATED", SETUP_URL, null)));
    }

    private static PasswordSetupRequestedEvent event(PasswordSetupRequestedEvent.Payload payload) {
        return new PasswordSetupRequestedEvent(EVENT_ID, EventTypes.PASSWORD_SETUP_REQUESTED, NOW, payload);
    }

    private static PasswordSetupRequestedEvent.Payload payload(String fullName, Instant expiresAt) {
        return payload(fullName, expiresAt, "ACCOUNT_CREATED");
    }

    private static PasswordSetupRequestedEvent.Payload payload(String fullName, Instant expiresAt, String purpose) {
        return new PasswordSetupRequestedEvent.Payload(USER_ID, "juan@mail.com", fullName, "DRIVER", purpose, SETUP_URL,
                expiresAt);
    }

    private static String rowValue(MailRequest request, String label) {
        return request.view().getRows().stream().filter(row -> row.getLabel().equals(label))
                .map(NoticeRow::getValue).findFirst().orElse(null);
    }
}
