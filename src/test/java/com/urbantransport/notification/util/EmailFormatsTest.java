package com.urbantransport.notification.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailFormatsTest {

    private static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    private static final Locale GUATEMALA = Locale.forLanguageTag("es-GT");
    private static final LocalDate NEXT_SUNDAY = LocalDate.now(ZONE).with(TemporalAdjusters.next(DayOfWeek.SUNDAY));

    @ParameterizedTest
    @CsvSource({"1234.5, 'Q 1,234.50'", "0, Q 0.00", "45, Q 45.00", "1000000.456, 'Q 1,000,000.46'"})
    void money_usesQuetzalSymbolThousandsAndTwoDecimals(String amount, String expected) {
        assertEquals(expected, EmailFormats.money(new BigDecimal(amount)));
    }

    @Test
    void money_withoutAmount_returnsDash() {
        assertEquals("—", EmailFormats.money(null));
    }

    @Test
    void longDateTime_isWrittenInSpanishWithDayAndMonthNames() {
        LocalDateTime value = NEXT_SUNDAY.atTime(6, 5);

        String expected = "domingo " + NEXT_SUNDAY.getDayOfMonth() + " de "
                + NEXT_SUNDAY.getMonth().getDisplayName(TextStyle.FULL, GUATEMALA) + " de " + NEXT_SUNDAY.getYear()
                + ", 06:05";
        assertEquals(expected, EmailFormats.longDateTime(value));
    }

    @Test
    void shortDateTime_usesDayMonthYearAndTwentyFourHours() {
        LocalDateTime value = NEXT_SUNDAY.atTime(18, 30);

        assertEquals(String.format("%02d/%02d/%d 18:30", NEXT_SUNDAY.getDayOfMonth(), NEXT_SUNDAY.getMonthValue(),
                NEXT_SUNDAY.getYear()), EmailFormats.shortDateTime(value));
    }

    @Test
    void date_usesDayMonthYear() {
        assertEquals(String.format("%02d/%02d/%d", NEXT_SUNDAY.getDayOfMonth(), NEXT_SUNDAY.getMonthValue(),
                NEXT_SUNDAY.getYear()), EmailFormats.date(NEXT_SUNDAY));
    }

    @Test
    void date_withoutValue_returnsDash() {
        assertEquals("—", EmailFormats.date(null));
    }

    @Test
    void shortCode_isFirstEightCharactersInUpperCase() {
        assertEquals("ABCDEF12", EmailFormats.shortCode(UUID.fromString("abcdef12-3456-7890-abcd-ef1234567890")));
    }
}
