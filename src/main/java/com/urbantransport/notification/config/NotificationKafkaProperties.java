package com.urbantransport.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.kafka")
public record NotificationKafkaProperties(String identityEventsTopic, String ticketEventsTopic) {
}