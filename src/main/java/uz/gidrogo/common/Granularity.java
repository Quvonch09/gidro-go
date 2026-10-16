package uz.gidrogo.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Grafik seriyalari donadorligi. Hafta dushanbadan boshlanadi; chegaralar Asia/Tashkent kunlari. */
public enum Granularity {
    DAY, WEEK, MONTH;

    public LocalDate bucketStart(LocalDate date) {
        return switch (this) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    public LocalDate next(LocalDate bucketStart) {
        return switch (this) {
            case DAY -> bucketStart.plusDays(1);
            case WEEK -> bucketStart.plusWeeks(1);
            case MONTH -> bucketStart.plusMonths(1);
        };
    }

    /**
     * from/to ni tekshiradi: from ≤ to, oraliq maxDays kundan oshmaydi, kelajak sanasi yo'q.
     */
    public static void validateRange(LocalDate from, LocalDate to, int maxDays) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "'from' sanasi 'to' sanasidan keyin bo'lishi mumkin emas");
        }
        if (to.isAfter(TashkentTime.today())) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "'to' kelajak sanasi bo'lishi mumkin emas");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1 > maxDays) {
            throw ApiException.badRequest("INVALID_DATE_RANGE", "Sana oralig'i " + maxDays + " kundan oshmasligi kerak");
        }
    }
}
