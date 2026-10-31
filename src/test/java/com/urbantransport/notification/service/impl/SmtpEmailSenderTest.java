package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationMailProperties;
import com.urbantransport.notification.exception.EmailSendException;
import com.urbantransport.notification.service.EmailMessage;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.ConnectException;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    private static final String FROM = "transporte@gmail.com";
    private static final String FROM_NAME = "Code'n Bugs Transporte";
    private static final NotificationMailProperties PROPERTIES =
            new NotificationMailProperties(true, FROM_NAME, FROM, "");

    @Mock
    private JavaMailSender mailSender;

    @Captor
    private ArgumentCaptor<MimeMessage> mimeCaptor;

    private SmtpEmailSender sender;

    @BeforeEach
    void setUp() {
        sender = new SmtpEmailSender(mailSender, PROPERTIES, "app-password");
    }

    @Nested
    class Constructor {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void withoutFromAddress_failsAtStartup(String fromAddress) {
            NotificationMailProperties properties = new NotificationMailProperties(true, FROM_NAME, fromAddress, "");

            assertThrows(IllegalStateException.class, () -> new SmtpEmailSender(mailSender, properties, "secret"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  "})
        void withoutPassword_failsAtStartup(String password) {
            assertThrows(IllegalStateException.class, () -> new SmtpEmailSender(mailSender, PROPERTIES, password));
        }
    }

    @Nested
    class Send {

        @Test
        void validMessage_isSentAsUtf8HtmlFromConfiguredAccount() throws Exception {
            when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

            sender.send(new EmailMessage("ana@mail.com", "Tu compración", "<p>Hola</p>"));

            verify(mailSender).send(mimeCaptor.capture());
            MimeMessage sent = mimeCaptor.getValue();
            InternetAddress from = (InternetAddress) sent.getFrom()[0];
            assertEquals(FROM, from.getAddress());
            assertEquals(FROM_NAME, from.getPersonal());
            assertEquals("ana@mail.com", sent.getAllRecipients()[0].toString());
            assertEquals("Tu compración", sent.getSubject());
            assertEquals("<p>Hola</p>", sent.getContent());
            assertTrue(sent.getDataHandler().getContentType().startsWith("text/html"));
        }

        @Test
        void invalidRecipient_throwsPermanentWithoutTalkingToSmtp() {
            EmailSendException exception = assertThrows(EmailSendException.class,
                    () -> sender.send(new EmailMessage("ana@", "Asunto", "<p></p>")));

            assertTrue(exception.isPermanent());
            verifyNoInteractions(mailSender);
        }

        @Test
        void rejectedCredentials_isTemporary() {
            MailAuthenticationException cause = new MailAuthenticationException("535 bad credentials");
            failOnSend(cause);

            EmailSendException exception = assertThrows(EmailSendException.class, () -> sender.send(message()));

            assertFalse(exception.isPermanent());
            assertSame(cause, exception.getCause());
            assertEquals("Gmail rechazó las credenciales: revisa MAIL_USERNAME y MAIL_PASSWORD",
                    exception.getMessage());
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.SmtpEmailSenderTest#permanentFailures")
        void addressProblem_isPermanent(MailException cause) {
            failOnSend(cause);

            EmailSendException exception = assertThrows(EmailSendException.class, () -> sender.send(message()));

            assertTrue(exception.isPermanent());
            assertSame(cause, exception.getCause());
            assertTrue(exception.getMessage().startsWith("No se pudo enviar el correo: "));
        }

        @ParameterizedTest
        @MethodSource("com.urbantransport.notification.service.impl.SmtpEmailSenderTest#temporaryFailures")
        void networkOrServerProblem_isTemporary(MailException cause) {
            failOnSend(cause);

            EmailSendException exception = assertThrows(EmailSendException.class, () -> sender.send(message()));

            assertFalse(exception.isPermanent());
            assertSame(cause, exception.getCause());
        }

        @Test
        void messageThatCannotBeBuilt_isPermanent() throws Exception {
            MimeMessage mime = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mime);
            doThrow(new MessagingException("encabezado roto")).when(mime).setFrom(any(Address.class));

            EmailSendException exception = assertThrows(EmailSendException.class, () -> sender.send(message()));

            assertTrue(exception.isPermanent());
            assertEquals("No se pudo armar el correo: encabezado roto", exception.getMessage());
            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        private void failOnSend(MailException cause) {
            when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
            doThrow(cause).when(mailSender).send(any(MimeMessage.class));
        }
    }

    static Stream<MailException> permanentFailures() throws AddressException {
        Address invalid = new InternetAddress("ana@mail.com");
        return Stream.of(
                new MailParseException("direccion mal formada"),
                new MailSendException("rechazado", new AddressException("ilegal")),
                new MailSendException("rechazado", new MessagingException("envoltura",
                        new SendFailedException("550", null, new Address[0], new Address[0],
                                new Address[]{invalid}))),
                new MailSendException(Map.<Object, Exception>of(new Object(), new SendFailedException("550", null, new Address[0],
                        new Address[0], new Address[]{invalid}))));
    }

    static Stream<MailException> temporaryFailures() {
        return Stream.of(
                new MailSendException("conexion rechazada", new ConnectException("refused")),
                new MailSendException("sin direcciones invalidas", new SendFailedException("451", null,
                        new Address[0], new Address[0], new Address[0])),
                new MailSendException("sin lista", new SendFailedException("451")),
                new MailSendException(Map.<Object, Exception>of(new Object(), new MessagingException("421 servicio no disponible"))));
    }

    private static EmailMessage message() {
        return new EmailMessage("ana@mail.com", "Asunto", "<p>Hola</p>");
    }
}
