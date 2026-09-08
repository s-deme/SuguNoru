package jp.sugunoru.app.data;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts published departure times from a public timetable page.
 *
 * <p>The markup used by Japanese transport operators is not standardized, so this parser
 * intentionally supports the two stable representations that commonly appear on official pages:
 * complete clock values (for example {@code 07:35}) and hourly table rows (for example
 * {@code 07 | 05 18 35}). It never executes page scripts or follows links embedded in a page.
 */
public final class OfficialTimetableParser {
    private static final Pattern TABLE = Pattern.compile("(?is)<table\\b[^>]*>(.*?)</table\\s*>");
    private static final Pattern ROW = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr\\s*>");
    private static final Pattern CLOCK = Pattern.compile(
            "(?<!\\d)([01]?\\d|2[0-3])\\s*(?:[:：]|時\\s*)\\s*([0-5]?\\d)(?:\\s*分)?(?!\\d)");
    private static final Pattern COMPACT_CLOCK = Pattern.compile(
            "(?<!\\d)([01]\\d|2[0-3])([0-5]\\d)(?!\\d)");
    private static final Pattern NUMBER = Pattern.compile("(?<!\\d)\\d{1,2}(?!\\d)");
    private static final Pattern LEADING_HOUR = Pattern.compile(
            "^\\s*(?:午前\\s*)?([01]?\\d|2[0-3])\\s*(?:時)?(?=\\s|$)");
    private static final Pattern JSON_ARRAY = Pattern.compile(
            "(?is)\\\"([^\\\"]+)\\\"\\s*:\\s*\\[(.*?)\\]");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x[0-9a-fA-F]+|\\d+);");

    private OfficialTimetableParser() {}

    public record Timetable(
            List<LocalTime> weekdayTimes,
            List<LocalTime> weekendTimes,
            List<LocalTime> holidayTimes
    ) {
        public Timetable {
            weekdayTimes = immutableSorted(weekdayTimes);
            weekendTimes = immutableSorted(weekendTimes);
            holidayTimes = immutableSorted(holidayTimes);
            if (weekdayTimes.isEmpty() && weekendTimes.isEmpty() && holidayTimes.isEmpty()) {
                throw new IllegalArgumentException("公式ページから発車時刻を見つけられませんでした");
            }
        }

        public boolean hasAnyTimes() {
            return !weekdayTimes.isEmpty() || !weekendTimes.isEmpty() || !holidayTimes.isEmpty();
        }

        private static List<LocalTime> immutableSorted(List<LocalTime> values) {
            return Collections.unmodifiableList(new ArrayList<>(new TreeSet<>(
                    values == null ? List.of() : values)));
        }
    }

    public static Timetable parse(String document, String contentType) {
        String trimmed = document == null ? "" : document.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("公式ページの内容が空です");
        }
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.contains("json") || trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return parseJson(trimmed);
        }
        return parseHtml(trimmed);
    }

    public static Timetable parseHtml(String html) {
        String sanitized = removeIgnoredMarkup(html);
        List<Section> sections = new ArrayList<>();
        Matcher tables = TABLE.matcher(sanitized);
        int previousTableEnd = 0;
        while (tables.find()) {
            List<LocalTime> times = extractTimesFromHtmlFragment(tables.group(1));
            if (!times.isEmpty()) {
                int contextStart = Math.max(previousTableEnd, Math.max(0, tables.start() - 350));
                String context = sanitized.substring(contextStart, tables.end());
                sections.add(new Section(classify(context), times));
            }
            previousTableEnd = tables.end();
        }

        if (sections.isEmpty()) {
            return new Timetable(extractTimesFromHtmlFragment(sanitized), List.of(), List.of());
        }
        return assembleTimetable(sections);
    }

    private static Timetable assembleTimetable(List<Section> sections) {
        List<LocalTime> weekday = new ArrayList<>();
        List<LocalTime> weekend = new ArrayList<>();
        List<LocalTime> holiday = new ArrayList<>();
        List<List<LocalTime>> unclassified = new ArrayList<>();
        for (Section section : sections) {
            switch (section.group()) {
                case WEEKDAY -> weekday.addAll(section.times());
                case WEEKEND -> weekend.addAll(section.times());
                case HOLIDAY -> holiday.addAll(section.times());
                case WEEKEND_AND_HOLIDAY -> {
                    weekend.addAll(section.times());
                    holiday.addAll(section.times());
                }
                case UNKNOWN -> unclassified.add(section.times());
            }
        }

        // A number of official pages show several unnamed tables in weekday, weekend, holiday
        // order. Keep that useful convention while preferring explicit labels whenever present.
        for (List<LocalTime> times : unclassified) {
            if (weekday.isEmpty()) weekday.addAll(times);
            else if (weekend.isEmpty()) weekend.addAll(times);
            else if (holiday.isEmpty()) holiday.addAll(times);
            else weekday.addAll(times);
        }
        return new Timetable(weekday, weekend, holiday);
    }

    public static Timetable parseJson(String json) {
        List<Section> sections = new ArrayList<>();
        Matcher arrays = JSON_ARRAY.matcher(json);
        while (arrays.find()) {
            List<LocalTime> times = extractTimesFromJsonArray(arrays.group(2));
            if (times.isEmpty()) continue;
            sections.add(new Section(classify(arrays.group(1)), times));
        }
        if (sections.isEmpty()) {
            return new Timetable(extractTimesFromJsonArray(json), List.of(), List.of());
        }
        return assembleTimetable(sections);
    }

    private static List<LocalTime> extractTimesFromHtmlFragment(String html) {
        TreeSet<LocalTime> result = new TreeSet<>();
        String text = toVisibleText(html);
        result.addAll(extractClockTimes(text));
        for (String line : text.split("\\R")) result.addAll(extractHourlyRow(line));
        Matcher rows = ROW.matcher(html);
        while (rows.find()) result.addAll(extractHourlyRow(toVisibleText(rows.group(1))));
        return new ArrayList<>(result);
    }

    private static List<LocalTime> extractTimesFromJsonArray(String source) {
        TreeSet<LocalTime> result = new TreeSet<>(extractClockTimes(source));
        Matcher compact = COMPACT_CLOCK.matcher(source);
        while (compact.find()) {
            addTime(result, compact.group(1), compact.group(2));
        }
        return new ArrayList<>(result);
    }

    private static List<LocalTime> extractClockTimes(String text) {
        TreeSet<LocalTime> result = new TreeSet<>();
        Matcher clocks = CLOCK.matcher(text);
        while (clocks.find()) addTime(result, clocks.group(1), clocks.group(2));
        return new ArrayList<>(result);
    }

    private static List<LocalTime> extractHourlyRow(String row) {
        Matcher leading = LEADING_HOUR.matcher(row);
        if (!leading.find()) return List.of();
        int hour;
        try {
            hour = Integer.parseInt(leading.group(1));
        } catch (NumberFormatException error) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>();
        Matcher numbers = NUMBER.matcher(row);
        while (numbers.find()) values.add(Integer.parseInt(numbers.group()));
        if (values.size() < 2 || values.get(0) != hour) return List.of();

        TreeSet<LocalTime> result = new TreeSet<>();
        for (int index = 1; index < values.size(); index++) {
            int minute = values.get(index);
            if (minute >= 0 && minute <= 59) result.add(LocalTime.of(hour, minute));
        }
        return new ArrayList<>(result);
    }

    private static void addTime(TreeSet<LocalTime> result, String hourValue, String minuteValue) {
        try {
            int hour = Integer.parseInt(hourValue);
            int minute = Integer.parseInt(minuteValue);
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                result.add(LocalTime.of(hour, minute));
            }
        } catch (RuntimeException ignored) {
            // A malformed token is not enough to invalidate the other published times.
        }
    }

    private static String removeIgnoredMarkup(String html) {
        return (html == null ? "" : html)
                .replaceAll("(?is)<(script|style|noscript|svg|template)\\b[^>]*>.*?</\\1\\s*>", " ");
    }

    private static String toVisibleText(String html) {
        String text = removeIgnoredMarkup(html)
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</(?:tr|p|div|li|h[1-6]|table|section|article)\\s*>", "\n")
                .replaceAll("(?is)<[^>]+>", " ");
        return decodeEntities(text).replace('\u00a0', ' ').replaceAll("[ \\t]+", " ");
    }

    private static String decodeEntities(String value) {
        String decoded = value.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&colon;", ":");
        Matcher entities = NUMERIC_ENTITY.matcher(decoded);
        StringBuffer result = new StringBuffer();
        while (entities.find()) {
            try {
                String token = entities.group(1);
                int codePoint = token.charAt(0) == 'x' || token.charAt(0) == 'X'
                        ? Integer.parseInt(token.substring(1), 16) : Integer.parseInt(token);
                entities.appendReplacement(result,
                        Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
            } catch (RuntimeException error) {
                entities.appendReplacement(result, Matcher.quoteReplacement(entities.group()));
            }
        }
        entities.appendTail(result);
        return result.toString();
    }

    private static Group classify(String text) {
        String normalized = toVisibleText(text).toLowerCase(Locale.ROOT)
                .replace('～', '〜').replace('−', '-');
        boolean weekday = normalized.contains("平日") || normalized.contains("weekday")
                || normalized.contains("月〜金") || normalized.contains("月-金")
                || normalized.contains("mon-fri");
        boolean weekend = normalized.contains("土日") || normalized.contains("土曜")
                || normalized.contains("土曜日") || normalized.contains("日曜")
                || normalized.contains("日曜日") || normalized.contains("weekend");
        boolean holiday = normalized.contains("土日祝") || normalized.contains("祝日") || normalized.contains("休日")
                || normalized.contains("holiday");
        if (weekend && holiday) return Group.WEEKEND_AND_HOLIDAY;
        if (weekday) return Group.WEEKDAY;
        if (weekend) return Group.WEEKEND;
        if (holiday) return Group.HOLIDAY;
        return Group.UNKNOWN;
    }

    private record Section(Group group, List<LocalTime> times) {}

    private enum Group { WEEKDAY, WEEKEND, HOLIDAY, WEEKEND_AND_HOLIDAY, UNKNOWN }
}
