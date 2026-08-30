package jp.sugunoru.app.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.Set;

public final class ScheduleEngine {
    private ScheduleEngine() {}

    public record Departure(LocalDateTime at, long waitMinutes, boolean nextDay) {}

    public record RouteOption(
            RoutePlan plan,
            Departure departure,
            LocalDateTime estimatedArrival
    ) {}

    public static List<Departure> nextDepartures(RoutePlan plan, LocalDateTime now, int limit) {
        return nextDepartures(plan, now, limit, Set.of());
    }

    public static List<Departure> nextDepartures(
            RoutePlan plan, LocalDateTime now, int limit, Set<LocalDate> holidays
    ) {
        List<Departure> result = new ArrayList<>();
        LocalDateTime readyAt = now.plusMinutes(plan.walkMinutes());

        for (int dayOffset = 0; dayOffset < 8 && result.size() < limit; dayOffset++) {
            LocalDate date = now.toLocalDate().plusDays(dayOffset);
            for (LocalTime time : timesFor(plan, date, holidays)) {
                LocalDateTime candidate = LocalDateTime.of(date, time);
                if (!candidate.isBefore(readyAt)) {
                    long seconds = Math.max(0, Duration.between(now, candidate).getSeconds());
                    long wait = (seconds + 59) / 60;
                    result.add(new Departure(candidate, wait, dayOffset > 0));
                    if (result.size() == limit) break;
                }
            }
        }
        return result;
    }

    public static List<RouteOption> compare(
            List<RoutePlan> plans, RoutePlan.Direction direction, LocalDateTime now
    ) {
        return compare(plans, direction, now, Set.of());
    }

    public static List<RouteOption> compare(
            List<RoutePlan> plans, RoutePlan.Direction direction, LocalDateTime now,
            Set<LocalDate> holidays
    ) {
        List<RouteOption> result = new ArrayList<>();
        for (RoutePlan plan : plans) {
            if (plan.direction() != direction || !plan.enabled()) continue;
            List<Departure> next = nextDepartures(plan, now, 1, holidays);
            if (!next.isEmpty()) {
                Departure departure = next.get(0);
                result.add(new RouteOption(plan, departure,
                        departure.at().plusMinutes(plan.rideMinutes() + plan.finalWalkMinutes())));
            }
        }
        result.sort(Comparator
                .comparing(RouteOption::estimatedArrival)
                .thenComparing(option -> option.departure().at()));
        return result;
    }

    public static List<LocalTime> timesFor(RoutePlan plan, LocalDate date) {
        return timesFor(plan, date, Set.of());
    }

    public static List<LocalTime> timesFor(RoutePlan plan, LocalDate date, Set<LocalDate> holidays) {
        if (holidays.contains(date) && !plan.holidayTimes().isEmpty()) return plan.holidayTimes();
        DayOfWeek day = date.getDayOfWeek();
        boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        List<LocalTime> primary = weekend ? plan.weekendTimes() : plan.weekdayTimes();
        if (!primary.isEmpty()) return primary;
        return weekend ? plan.weekdayTimes() : plan.weekendTimes();
    }

    public static List<LocalTime> parseTimes(String raw) {
        if (raw == null || raw.trim().isEmpty()) return List.of();
        String normalized = raw.trim()
                .replace('：', ':')
                .replace('、', ' ')
                .replace(',', ' ');
        String[] tokens = normalized.split("[\\s]+", -1);
        List<LocalTime> result = new ArrayList<>();
        for (String token : tokens) {
            if (token.isBlank()) continue;
            String value = token.matches("\\d{3,4}")
                    ? token.substring(0, token.length() - 2) + ":" + token.substring(token.length() - 2)
                    : token;
            try {
                String[] parts = value.split(":", -1);
                if (parts.length != 2) throw new RuntimeException();
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                result.add(LocalTime.of(hour, minute));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("「" + token + "」は時刻として読めません（例 07:35）");
            }
        }
        return new ArrayList<>(new TreeSet<>(result));
    }

    public static String formatMinutes(long minutes) {
        if (minutes < 60) return minutes + "分後";
        return String.format(Locale.JAPAN, "%d時間%02d分後", minutes / 60, minutes % 60);
    }
}
