package com.urbantransport.notification.service;

import java.util.Map;

public interface EmailRenderer {

    /**
     * Arma el HTML de una plantilla (templates/email/{template}.html).
     * Además de las variables recibidas, toda plantilla tiene "brand" y "year".
     */
    String render(String template, Map<String, Object> variables);
}