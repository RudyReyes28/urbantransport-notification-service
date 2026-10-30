package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.service.EmailQueueService;
import com.urbantransport.notification.service.EmailRenderer;
import com.urbantransport.notification.service.EmailRequest;
import com.urbantransport.notification.service.EventMailer;
import com.urbantransport.notification.service.MailRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EventMailerImpl implements EventMailer {

    private static final String NOTICE_TEMPLATE = "notice";
    private static final int MAX_SUBJECT = 200;
    private static final String DEFAULT_SKIP_REASON = "Sin destinatarios";

    private final EmailQueueService emailQueueService;
    private final EmailRenderer emailRenderer;

    @Override
    public void send(MailRequest request) {
        String subject = request.subject().length() <= MAX_SUBJECT
                ? request.subject()
                : request.subject().substring(0, MAX_SUBJECT - 1) + "…";

        List<String> recipients = request.recipients() == null ? List.of() : request.recipients().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .map(email -> email.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();

        if (recipients.isEmpty()) {
            emailQueueService.skip(request.eventId(), request.eventType(), request.referenceId(),
                    request.template(), subject,
                    request.skipReason() == null ? DEFAULT_SKIP_REASON : request.skipReason());
            return;
        }

        String html = emailRenderer.render(NOTICE_TEMPLATE, request.view().toVariables());
        for (String recipient : recipients) {
            emailQueueService.enqueue(new EmailRequest(request.eventId(), request.eventType(),
                    request.referenceId(), recipient, request.template(), subject, html));
        }
    }
}