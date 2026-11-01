package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationMailProperties;
import com.urbantransport.notification.service.EmailRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Year;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ThymeleafEmailRenderer implements EmailRenderer {

    private static final Locale LOCALE = Locale.forLanguageTag("es-GT");

    private final TemplateEngine templateEngine;
    private final NotificationMailProperties properties;

    @Override
    public String render(String template, Map<String, Object> variables) {
        Context context = new Context(LOCALE);
        context.setVariables(variables);
        context.setVariable("brand", properties.fromName());
        context.setVariable("year", Year.now().getValue());
        return templateEngine.process("email/" + template, context);
    }
}