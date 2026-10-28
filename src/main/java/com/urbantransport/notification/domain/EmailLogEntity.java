package com.urbantransport.notification.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** Un correo: a la vez cola de envío (PENDING) e historial (SENT, FAILED, SKIPPED). */
@Entity
@Table(name = "email_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailLogEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    /** orderId o ticketId, para rastrear el correo desde la compra. */
    @Column(name = "reference_id")
    private UUID referenceId;

    /** Nulo cuando el correo se omite por no tener destinatario. */
    @Column(length = 150)
    private String recipient;

    @Column(nullable = false, length = 60)
    private String template;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EmailStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    /** PENDING: cuándo toca (re)intentar. Mientras se envía, se corre hacia adelante (arrendamiento). */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;
}