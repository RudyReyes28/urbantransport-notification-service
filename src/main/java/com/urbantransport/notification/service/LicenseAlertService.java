package com.urbantransport.notification.service;

public interface LicenseAlertService {

    /** Revisa las licencias y envía los avisos que correspondan. Devuelve cuántos choferes fueron avisados. */
    int runCheck();
}