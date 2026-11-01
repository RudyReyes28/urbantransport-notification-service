package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.exception.EmailSendException;
import com.urbantransport.notification.service.EmailMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogEmailSenderTest {

    private final LogEmailSender sender = new LogEmailSender();

    @Test
    void validRecipient_isOnlyLogged() {
        assertDoesNotThrow(() -> sender.send(new EmailMessage("ana@mail.com", "Asunto", "<p>Hola</p>")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana", "ana@mail.com, otro@mail.com", "Ana <ana@mail.com>"})
    void invalidRecipient_throwsPermanentFailure(String to) {
        EmailSendException exception = assertThrows(EmailSendException.class,
                () -> sender.send(new EmailMessage(to, "Asunto", "<p>Hola</p>")));

        assertTrue(exception.isPermanent());
        assertEquals("Destinatario inválido: " + to, exception.getMessage());
    }
}
