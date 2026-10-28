package com.urbantransport.notification.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Formatos para los correos. Las fechas llegan en hora de Guatemala (sin zona), así que no se convierten. */
public final class EmailFormats {

    private static final Locale GUATEMALA = Locale.forLanguageTag("es-GT");
    private static final DateTimeFormatter LONG_DATE_TIME =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm", GUATEMALA);
    private static final DateTimeFormatter SHORT_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private EmailFormats() {
    }

    /** "Q 1,234.50" */
    public static String money(BigDecimal amount) {
        return amount == null ? "—" : String.format(Locale.US, "Q %,.2f", amount);
    }

    /** "domingo 25 de octubre de 2026, 06:00" */
    public static String longDateTime(LocalDateTime value) {
        return value.format(LONG_DATE_TIME);
    }

    /** "25/10/2026 06:00" */
    public static String shortDateTime(LocalDateTime value) {
        return value.format(SHORT_DATE_TIME);
    }

    public static String date(LocalDate value) {
        return value == null ? "—" : value.format(DATE);
    }

    /** Código corto para que el cliente cite su orden: los primeros 8 caracteres del id. */
    public static String shortCode(UUID id) {
        return id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }
}