package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationEventProperties;
import com.urbantransport.notification.domain.ProcessedEventEntity;
import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.NoticeView;
import com.urbantransport.notification.dto.Tone;
import com.urbantransport.notification.event.EventTypes;
import com.urbantransport.notification.event.WalletRechargedEvent;
import com.urbantransport.notification.repository.ProcessedEventRepository;
import com.urbantransport.notification.service.EventMailer;
import com.urbantransport.notification.service.MailRequest;
import com.urbantransport.notification.service.RecipientResolver;
import com.urbantransport.notification.service.WalletNotificationService;
import com.urbantransport.notification.util.EmailFormats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletNotificationServiceImpl implements WalletNotificationService {

    private static final String TEMPLATE = "wallet-recharged";

    private final ProcessedEventRepository processedEventRepository;
    private final RecipientResolver recipientResolver;
    private final EventMailer eventMailer;
    private final NotificationEventProperties eventProperties;
    private final Clock clock;

    @Override
    @Transactional
    public void handleRecharged(UUID eventId, Instant occurredAt, WalletRechargedEvent.Payload payload) {
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Evento {} ya procesado, se ignora", eventId);
            return;
        }
        if (occurredAt == null || payload == null || payload.customerId() == null || payload.amount() == null) {
            throw new IllegalArgumentException("WalletRecharged incompleto: " + eventId);
        }

        String subject = "Recarga recibida: " + EmailFormats.money(payload.amount());
        if (Duration.between(occurredAt, Instant.now(clock)).compareTo(eventProperties.maxWalletEventAge()) > 0) {
            eventMailer.send(new MailRequest(eventId, EventTypes.WALLET_RECHARGED, payload.customerId(), TEMPLATE,
                    subject, null, List.of(), "Evento de hace más de " + eventProperties.maxWalletEventAge()));
        } else {
            NoticeView view = NoticeView.of(Tone.SUCCESS, "Recarga recibida",
                    "Hola, acreditamos " + EmailFormats.money(payload.amount()) + " a tu cartera.",
                    List.of(new NoticeRow("Monto", EmailFormats.money(payload.amount())),
                            new NoticeRow("Fecha de pago", EmailFormats.date(payload.paymentDate()))),
                    "El saldo ya está disponible para comprar boletos y pagar alquileres.");
            eventMailer.send(new MailRequest(eventId, EventTypes.WALLET_RECHARGED, payload.customerId(), TEMPLATE,
                    subject, view, recipientResolver.customerEmail(payload.customerId()),
                    "El cliente no tiene correo en el directorio"));
        }
        processedEventRepository.save(ProcessedEventEntity.builder().eventId(eventId).build());
    }
}