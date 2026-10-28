package com.urbantransport.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Eventos más viejos que esto no generan correo (quedan SKIPPED): evita reenviar el historial al arrancar. */
@ConfigurationProperties(prefix = "notification.events")
public record NotificationEventProperties(Duration maxPurchaseAge, Duration maxCancellationAge) {
}