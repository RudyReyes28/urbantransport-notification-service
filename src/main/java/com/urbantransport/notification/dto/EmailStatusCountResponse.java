package com.urbantransport.notification.dto;

import com.urbantransport.notification.domain.EmailStatus;
import lombok.Value;

@Value
public class EmailStatusCountResponse {
    EmailStatus status;
    long total;
}