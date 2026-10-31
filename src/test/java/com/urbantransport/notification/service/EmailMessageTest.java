package com.urbantransport.notification.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailMessageTest {

    @Test
    void lineBreaksInRecipientAndSubject_areReplacedBySpacesAndTrimmed() {
        EmailMessage message = new EmailMessage(" ana@mail.com\r\n", "Hola\r\nBcc: otro@mail.com\n", "<p>a\nb</p>");

        assertEquals("ana@mail.com", message.to());
        assertEquals("Hola Bcc: otro@mail.com", message.subject());
        assertEquals("<p>a\nb</p>", message.htmlBody());
    }

    @Test
    void nullRecipient_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> new EmailMessage(null, "Asunto", "<p></p>"));
    }

    @Test
    void nullSubject_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> new EmailMessage("ana@mail.com", null, "<p></p>"));
    }

    @Test
    void nullBody_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> new EmailMessage("ana@mail.com", "Asunto", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@mail.com", "ana.lopez+boletos@sub.mail.com.gt"})
    void wellFormedAddress_isValidRecipient(String to) {
        assertTrue(new EmailMessage(to, "Asunto", "<p></p>").hasValidRecipient());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ana", "ana@mail", "ana@@mail.com", "ana@mail.com, otro@mail.com",
            "ana@mail.com;otro@mail.com", "Ana <ana@mail.com>", "ana lopez@mail.com", "\r\nana@mail.com\r\nx@y.z"})
    void malformedAddress_isNotValidRecipient(String to) {
        assertFalse(new EmailMessage(to, "Asunto", "<p></p>").hasValidRecipient());
    }
}
