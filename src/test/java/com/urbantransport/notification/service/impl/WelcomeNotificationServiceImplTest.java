package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationEventProperties;
import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.Tone;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.event.UserEvent;
import com.urbantransport.notification.service.EventMailer;
import com.urbantransport.notification.service.MailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
class WelcomeNotificationServiceImplTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final NotificationEventProperties PROPERTIES = new NotificationEventProperties(Duration.ofHours(24),
            Duration.ofDays(7), Duration.ofDays(2), Duration.ofDays(2), Duration.ofDays(2), Duration.ofDays(2));

    @Mock
    private EventMailer eventMailer;

    @Captor
    private ArgumentCaptor<MailRequest> mailCaptor;

    private WelcomeNotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WelcomeNotificationServiceImpl(eventMailer, PROPERTIES, CLOCK);
    }

    @Nested
    class HandleUserCreated {

        @Test
        void newCustomer_receivesCustomerWelcome() {
            service.handleUserCreated(event(NOW.minusSeconds(30), payload("CUSTOMER", "Ana Lopez", true)));

            verify(eventMailer).send(mailCaptor.capture());
            MailRequest mail = mailCaptor.getValue();
            assertEquals(EVENT_ID, mail.eventId());
            assertEquals(EventTypes.USER_CREATED, mail.eventType());
            assertEquals(USER_ID, mail.referenceId());
            assertEquals("welcome", mail.template());
            assertEquals("Te damos la bienvenida a Code'n Bugs", mail.subject());
            assertEquals(List.of("ana@mail.com"), mail.recipients());
            assertNull(mail.skipReason());
            assertEquals(Tone.SUCCESS, mail.view().getTone());
            assertEquals("Hola Ana Lopez, tu cuenta fue creada.", mail.view().getIntro());
            assertEquals("Cliente", rowValue(mail, "Tipo de cuenta"));
            assertEquals("ana@mail.com", rowValue(mail, "Correo de acceso"));
        }

        @ParameterizedTest
        @CsvSource(value = {"SYSTEM_ADMIN, Administrador del sistema", "BRANCH_ADMIN, Administrador de sucursal",
                "CASHIER, Cajero", "DRIVER, Chofer", "AUDITOR, AUDITOR", "NULL, Usuario"}, nullValues = "NULL")
        void newStaffUser_receivesStaffWelcomeWithRoleLabel(String role, String label) {
            service.handleUserCreated(event(NOW, payload(role, "Luis", true)));

            verify(eventMailer).send(mailCaptor.capture());
            assertEquals(Tone.INFO, mailCaptor.getValue().view().getTone());
            assertEquals("Hola Luis, se creó tu cuenta de acceso al sistema.", mailCaptor.getValue().view().getIntro());
            assertEquals(label, rowValue(mailCaptor.getValue(), "Tipo de cuenta"));
        }

        @Test
        void userWithoutName_isGreetedWithoutName() {
            service.handleUserCreated(event(NOW, payload("CUSTOMER", null, true)));

            verify(eventMailer).send(mailCaptor.capture());
            assertEquals("Hola, tu cuenta fue creada.", mailCaptor.getValue().view().getIntro());
        }

        @Test
        void inactiveAccount_isSkipped() {
            service.handleUserCreated(event(NOW, payload("CASHIER", "Luis", false)));

            verify(eventMailer).send(mailCaptor.capture());
            assertTrue(mailCaptor.getValue().recipients().isEmpty());
            assertNull(mailCaptor.getValue().view());
            assertEquals("La cuenta nació inactiva", mailCaptor.getValue().skipReason());
        }

        @Test
        void staleAccount_isSkipped() {
            service.handleUserCreated(event(NOW.minus(3, ChronoUnit.DAYS), payload("CUSTOMER", "Ana", true)));

            verify(eventMailer).send(mailCaptor.capture());
            assertTrue(mailCaptor.getValue().recipients().isEmpty());
            assertEquals("Cuenta creada hace más de PT48H", mailCaptor.getValue().skipReason());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.WelcomeNotificationServiceImplTest#incomplete")
        void incompleteEvent_throwsAndSendsNothing(UserEvent event) {
            assertThrows(IllegalArgumentException.class, () -> service.handleUserCreated(event));

            verifyNoInteractions(eventMailer);
        }
    }

    static Stream<UserEvent> incomplete() {
        return Stream.of(
                event(NOW, null),
                event(null, payload("CUSTOMER", "Ana", true)),
                event(NOW, new UserEvent.UserPayload(null, "ana@mail.com", "Ana", "CUSTOMER", null, null, true)),
                event(NOW, new UserEvent.UserPayload(USER_ID, null, "Ana", "CUSTOMER", null, null, true)));
    }

    private static UserEvent event(Instant occurredAt, UserEvent.UserPayload payload) {
        return new UserEvent(EVENT_ID, EventTypes.USER_CREATED, occurredAt, payload);
    }

    private static UserEvent.UserPayload payload(String role, String fullName, boolean active) {
        return new UserEvent.UserPayload(USER_ID, "ana@mail.com", fullName, role, null, null, active);
    }

    private static String rowValue(MailRequest request, String label) {
        return request.view().getRows().stream().filter(row -> row.getLabel().equals(label))
                .map(NoticeRow::getValue).findFirst().orElse(null);
    }
}
