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
}
