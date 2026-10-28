package com.urbantransport.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * enabled=false: los correos solo se escriben en el log (no se envía nada).
 * fromAddress: Gmail siempre envía desde la cuenta autenticada; solo el nombre se puede personalizar.
 */
@ConfigurationProperties(prefix = "notification.mail")
public record NotificationMailProperties(
        boolean enabled,
        String fromName,
        String fromAddress,
        String smokeTestTo) {
}