package com.urbantransport.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "notification.dispatch")
public record NotificationDispatchProperties(
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff,
        Duration lease,
        int batchSize) {
}