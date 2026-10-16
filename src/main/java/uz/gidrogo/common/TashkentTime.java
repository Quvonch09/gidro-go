package uz.gidrogo.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Kun, hafta va oy chegaralari Asia/Tashkent bo'yicha; API'da vaqt UTC (Instant). */
public final class TashkentTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");

    private TashkentTime() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** Kun boshi (00:00 Toshkent) — UTC Instant. */
    public static Instant startOf(LocalDate day) {
        return day.atStartOfDay(ZONE).toInstant();
    }

    /** Oraliqning eksklyuziv oxiri: keyingi kun 00:00 (Toshkent). */
    public static Instant endExclusive(LocalDate lastDay) {
        return lastDay.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    public static LocalDate dateOf(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    /** O'sish foizi: (joriy − oldingi) ÷ oldingi × 100, 1 xona aniqlik. Oldingi 0 bo'lsa — null. */
    public static Double growthPercent(java.math.BigDecimal current, java.math.BigDecimal previous) {
        if (previous == null || previous.signum() == 0) return null;
        java.math.BigDecimal cur = current != null ? current : java.math.BigDecimal.ZERO;
        return cur.subtract(previous)
                .multiply(java.math.BigDecimal.valueOf(100))
                .divide(previous.abs(), 1, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static Double growthPercent(long current, long previous) {
        return growthPercent(java.math.BigDecimal.valueOf(current), java.math.BigDecimal.valueOf(previous));
    }

    public record DateRange(LocalDate start, LocalDate end) {
        public Instant startInstant() { return TashkentTime.startOf(start); }
        public Instant endInstant() { return TashkentTime.endExclusive(end); }
    }

    /**
     * period, date, startDate/endDate yoki from/to parametrlarini yagona [start, end] oralig'iga keltiradi.
     */
    public static LocalDate[] resolveRange(String period, LocalDate date, LocalDate startDate, LocalDate endDate,
                                           LocalDate from, LocalDate to) {
        if (date != null) {
            return new LocalDate[]{date, date};
        }
        LocalDate s = startDate != null ? startDate : from;
        LocalDate e = endDate != null ? endDate : to;
        if (s != null || e != null) {
            if (s == null) s = e;
            if (e == null) e = s;
            if (s.isAfter(e)) {
                LocalDate tmp = s;
                s = e;
                e = tmp;
            }
            return new LocalDate[]{s, e};
        }
        LocalDate today = today();
        if (period != null && !period.isBlank()) {
            return switch (period.toLowerCase(java.util.Locale.ROOT).trim()) {
                case "today", "bugun" -> new LocalDate[]{today, today};
                case "yesterday", "kecha" -> new LocalDate[]{today.minusDays(1), today.minusDays(1)};
                case "this_week", "week", "shu hafta", "7days", "7_days" -> new LocalDate[]{today.minusDays(6), today};
                case "this_month", "month", "shu oy" -> new LocalDate[]{today.withDayOfMonth(1), today};
                case "quarter", "chorak" -> new LocalDate[]{today.minusMonths(3).plusDays(1), today};
                case "this_year", "year", "shu yil" -> new LocalDate[]{today.withDayOfYear(1), today};
                default -> new LocalDate[]{today.minusDays(29), today};
            };
        }
        return new LocalDate[]{today.minusDays(29), today};
    }
}
