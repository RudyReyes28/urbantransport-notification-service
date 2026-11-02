package com.urbantransport.notification.service;

import com.urbantransport.notification.event.PasswordSetupRequestedEvent;

public interface PasswordSetupNotificationService {

    void handlePasswordSetupRequested(PasswordSetupRequestedEvent event);
}
