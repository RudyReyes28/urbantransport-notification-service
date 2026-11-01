package com.urbantransport.notification.service.impl;

import com.urbantransport.notification.config.NotificationMailProperties;
import com.urbantransport.notification.dto.NoticeRow;
import com.urbantransport.notification.dto.NoticeView;
import com.urbantransport.notification.dto.TicketLineView;
import com.urbantransport.notification.dto.Tone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThymeleafEmailRendererTest {

    private static final String BRAND = "Code'n Bugs Transporte";

    private ThymeleafEmailRenderer renderer;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        renderer = new ThymeleafEmailRenderer(engine,
                new NotificationMailProperties(false, BRAND, "no-reply@mail.com", ""));
    }

    @Test
    void notice_rendersViewWithBrandAndCurrentYear() {
        NoticeView view = NoticeView.of(Tone.DANGER, "Solicitud rechazada", "Hola, lo sentimos.",
                List.of(new NoticeRow("Motivo", "Sin buses disponibles")), "Puedes intentar otra fecha.");

        String html = renderer.render("notice", view.toVariables());

        assertTrue(html.contains("Solicitud rechazada"));
        assertTrue(html.contains("Hola, lo sentimos."));
        assertTrue(html.contains("Motivo"));
        assertTrue(html.contains("Sin buses disponibles"));
        assertTrue(html.contains("Puedes intentar otra fecha."));
        assertTrue(html.contains(Tone.DANGER.getHex()));
        assertTrue(html.contains("Code&#39;n Bugs Transporte"));
        assertTrue(html.contains(String.valueOf(Year.now().getValue())));
    }

    @Test
    void notice_withoutRowsNorNote_omitsTableAndNote() {
        String html = renderer.render("notice",
                NoticeView.of(Tone.INFO, "Aviso", "Texto", null, null).toVariables());

        assertFalse(html.contains("<table"));
        assertFalse(html.contains("font-size:13px"));
    }

    @Test
    void brandFromCaller_isReplacedByConfiguredBrand() {
        Map<String, Object> variables = new LinkedHashMap<>(
                NoticeView.of(Tone.INFO, "Aviso", "Texto", List.of(), null).toVariables());
        variables.put("brand", "Otra marca");

        String html = renderer.render("notice", variables);

        assertFalse(html.contains("Otra marca"));
        assertTrue(html.contains("Code&#39;n Bugs Transporte"));
    }

    @Test
    void ticketPurchased_rendersEachTicketLine() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("customerNit", "1234567");
        variables.put("origin", "Central");
        variables.put("destination", "Xela");
        variables.put("departure", "domingo, 06:00");
        variables.put("busPlate", "P-001");
        variables.put("orderCode", "ABCDEF12");
        variables.put("tickets", List.of(new TicketLineView("1A", "Q 45.00"), new TicketLineView("1B", "Q 46.00")));
        variables.put("total", "Q 91.00");
        variables.put("paymentDate", "01/01/2030");

        String html = renderer.render("ticket-purchased", variables);

        assertTrue(html.contains("1234567"));
        assertTrue(html.contains("Central → Xela"));
        assertTrue(html.contains("1A"));
        assertTrue(html.contains("Q 46.00"));
        assertTrue(html.contains("Q 91.00"));
        assertTrue(html.contains("ABCDEF12"));
    }
}
