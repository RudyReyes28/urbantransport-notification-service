package com.urbantransport.notification.repository;

import com.urbantransport.notification.domain.EmailStatus;

public interface EmailStatusCount {
    EmailStatus getStatus();

    long getTotal();
}