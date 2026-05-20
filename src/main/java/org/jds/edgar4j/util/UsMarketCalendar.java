package org.jds.edgar4j.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.temporal.TemporalAdjusters.dayOfWeekInMonth;
import static java.time.temporal.TemporalAdjusters.lastInMonth;

public final class UsMarketCalendar {

    private UsMarketCalendar() {
    }

    public static boolean isExpectedTradingDay(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != SATURDAY
                && dayOfWeek != SUNDAY
                && !isUsMarketHoliday(date);
    }

    public static LocalDate startDateForRecentTradingDays(LocalDate today, int tradingDays) {
        if (tradingDays <= 0) {
            return today;
        }

        LocalDate cursor = today;
        int seen = 0;
        while (true) {
            if (isExpectedTradingDay(cursor)) {
                seen++;
                if (seen == tradingDays) {
                    return cursor;
                }
            }
            cursor = cursor.minusDays(1);
        }
    }

    private static boolean isUsMarketHoliday(LocalDate date) {
        int year = date.getYear();
        return date.equals(observeFixedHoliday(LocalDate.of(year, 1, 1)))
                || date.equals(observeFixedHoliday(LocalDate.of(year + 1, 1, 1)))
                || date.equals(dayOfWeekInMonth(3, MONDAY).adjustInto(LocalDate.of(year, 1, 1)))
                || date.equals(dayOfWeekInMonth(3, MONDAY).adjustInto(LocalDate.of(year, 2, 1)))
                || date.equals(calculateGoodFriday(year))
                || date.equals(lastInMonth(MONDAY).adjustInto(LocalDate.of(year, 5, 1)))
                || date.equals(observeFixedHoliday(LocalDate.of(year, 6, 19)))
                || date.equals(observeFixedHoliday(LocalDate.of(year, 7, 4)))
                || date.equals(dayOfWeekInMonth(1, MONDAY).adjustInto(LocalDate.of(year, 9, 1)))
                || date.equals(dayOfWeekInMonth(4, THURSDAY).adjustInto(LocalDate.of(year, 11, 1)))
                || date.equals(observeFixedHoliday(LocalDate.of(year, 12, 25)));
    }

    private static LocalDate observeFixedHoliday(LocalDate holiday) {
        return switch (holiday.getDayOfWeek()) {
            case SATURDAY -> holiday.minusDays(1);
            case SUNDAY -> holiday.plusDays(1);
            default -> holiday;
        };
    }

    private static LocalDate calculateGoodFriday(int year) {
        LocalDate easterSunday = calculateEasterSunday(year);
        return easterSunday.minusDays(2);
    }

    private static LocalDate calculateEasterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
