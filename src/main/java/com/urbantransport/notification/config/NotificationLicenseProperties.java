package com.urbantransport.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "notification.license")
public record NotificationLicenseProperties(List<Integer> alertDays, boolean runOnStartup) {
}