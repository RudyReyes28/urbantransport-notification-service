package com.urbantransport.notification.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventHeader(java.util.UUID eventId, String eventType) {
}