package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.NoticeView;
import com.urbantransport.notification.dto.Tone;
import com.urbantransport.notification.service.EmailQueueService;
import com.urbantransport.notification.service.EmailRenderer;
import com.urbantransport.notification.service.EmailRequest;
import com.urbantransport.notification.service.MailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventMailerImplTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REFERENCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String TEMPLATE = "rental-quoted";
    private static final String HTML = "<html>aviso</html>";
    private static final NoticeView VIEW = NoticeView.of(Tone.SUCCESS, "Cotizado", "Hola",
            List.of(new NoticeRow("Precio", "Q 100.00")), "Nota");

    @Mock
    private EmailQueueService emailQueueService;

    @Mock
    private EmailRenderer emailRenderer;

    @Captor
    private ArgumentCaptor<EmailRequest> requestCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> variablesCaptor;

    private EventMailerImpl mailer;

    @BeforeEach
    void setUp() {
        mailer = new EventMailerImpl(emailQueueService, emailRenderer);
    }

    @Test
    void recipients_areNormalizedDeduplicatedAndRenderedOnce() {
        when(emailRenderer.render(eq("notice"), variablesCaptor.capture())).thenReturn(HTML);

        mailer.send(request("Asunto", Arrays.asList(" Ana@Mail.com ", "ana@mail.com", null, "  ", "luis@mail.com"),
                null));

        verify(emailRenderer).render(eq("notice"), any());
        assertEquals(Tone.SUCCESS.getHex(), variablesCaptor.getValue().get("accent"));
        assertEquals("Cotizado", variablesCaptor.getValue().get("heading"));
        verify(emailQueueService, times(2)).enqueue(requestCaptor.capture());
        List<EmailRequest> requests = requestCaptor.getAllValues();
        assertEquals(List.of("ana@mail.com", "luis@mail.com"), requests.stream().map(EmailRequest::recipient).toList());
        EmailRequest first = requests.getFirst();
        assertEquals(EVENT_ID, first.eventId());
        assertEquals("RentalQuoted", first.eventType());
        assertEquals(REFERENCE_ID, first.referenceId());
        assertEquals(TEMPLATE, first.template());
        assertEquals("Asunto", first.subject());
        assertEquals(HTML, first.htmlBody());
        verify(emailQueueService, never()).skip(any(), any(), any(), any(), any(), any());
    }

    @Test
    void subjectLongerThan200_isCutWithEllipsis() {
        when(emailRenderer.render(eq("notice"), any())).thenReturn(HTML);

        mailer.send(request("a".repeat(250), List.of("ana@mail.com"), null));

        verify(emailQueueService).enqueue(requestCaptor.capture());
        String subject = requestCaptor.getValue().subject();
        assertEquals(200, subject.length());
        assertEquals("a".repeat(199) + "…", subject);
    }

    @Test
    void subjectOfExactly200_isKept() {
        when(emailRenderer.render(eq("notice"), any())).thenReturn(HTML);

        mailer.send(request("a".repeat(200), List.of("ana@mail.com"), null));

        verify(emailQueueService).enqueue(requestCaptor.capture());
        assertEquals("a".repeat(200), requestCaptor.getValue().subject());
    }

    @ParameterizedTest
    @MethodSource("com.urbantransport.notification.service.impl.EventMailerImplTest#emptyRecipients")
    void withoutRecipients_recordsSkipWithReasonAndRendersNothing(Collection<String> recipients) {
        mailer.send(request("Asunto", recipients, "Sin cliente"));

        verify(emailQueueService).skip(EVENT_ID, "RentalQuoted", REFERENCE_ID, TEMPLATE, "Asunto", "Sin cliente");
        verify(emailQueueService, never()).enqueue(any());
        verifyNoInteractions(emailRenderer);
    }

    @Test
    void withoutRecipientsAndReason_usesDefaultReason() {
        mailer.send(new MailRequest(EVENT_ID, "RentalQuoted", REFERENCE_ID, TEMPLATE, "Asunto", null, List.of(), null));

        verify(emailQueueService).skip(eq(EVENT_ID), eq("RentalQuoted"), eq(REFERENCE_ID), eq(TEMPLATE), eq("Asunto"),
                eq("Sin destinatarios"));
        verify(emailQueueService, never()).enqueue(any());
        verifyNoInteractions(emailRenderer);
    }

    @Test
    void skippedSubject_isAlsoCut() {
        mailer.send(request("b".repeat(300), List.of(), "x"));

        verify(emailQueueService).skip(eq(EVENT_ID), anyString(), eq(REFERENCE_ID), eq(TEMPLATE),
                eq("b".repeat(199) + "…"), eq("x"));
    }

    static Stream<Collection<String>> emptyRecipients() {
        return Stream.of(null, List.of(), Arrays.asList(null, " ", ""));
    }

    private static MailRequest request(String subject, Collection<String> recipients, String skipReason) {
        return new MailRequest(EVENT_ID, "RentalQuoted", REFERENCE_ID, TEMPLATE, subject, VIEW, recipients,
                skipReason);
    }
}
