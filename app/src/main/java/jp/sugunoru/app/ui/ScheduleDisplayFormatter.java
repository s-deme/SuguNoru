package jp.sugunoru.app.ui;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Locale;

/** Formatting shared by the activity and the home-screen widget. */
public final class ScheduleDisplayFormatter {
    private static final Locale JAPANESE = Locale.JAPAN;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("H:mm", JAPANESE);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", JAPANESE);

    private ScheduleDisplayFormatter() {}

    public static String clock(TemporalAccessor value) {
        return CLOCK.format(value);
    }

    public static String time(TemporalAccessor value) {
        return TIME.format(value);
    }

    public static String date(LocalDate value) {
        return value.getMonthValue() + "月" + value.getDayOfMonth() + "日（"
                + value.getDayOfWeek().getDisplayName(TextStyle.SHORT, JAPANESE) + "）";
    }

    public static String times(List<LocalTime> values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) result.append(i % 6 == 0 ? '\n' : ' ');
            result.append(time(values.get(i)));
        }
        return result.toString();
    }
}
