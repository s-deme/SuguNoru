package jp.sugunoru.app.data;

import org.junit.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class OfficialTimetableParserTest {
    @Test public void parsesLabeledHourlyTablesFromAnOfficialStylePage() {
        String page = """
                <h2>平日</h2>
                <table><tr><th>5</th><td>05 37</td></tr><tr><th>6</th><td>15</td></tr></table>
                <h2>土曜・休日</h2>
                <table><tr><th>6</th><td>10 50</td></tr></table>
                """;

        OfficialTimetableParser.Timetable result = OfficialTimetableParser.parseHtml(page);

        assertEquals(List.of(LocalTime.of(5, 5), LocalTime.of(5, 37), LocalTime.of(6, 15)),
                result.weekdayTimes());
        assertEquals(List.of(LocalTime.of(6, 10), LocalTime.of(6, 50)), result.weekendTimes());
        assertEquals(List.of(LocalTime.of(6, 10), LocalTime.of(6, 50)), result.holidayTimes());
    }

    @Test public void parsesCompleteClockValuesAndIgnoresScripts() {
        String page = """
                <script>const misleading = '00:00 23:59';</script>
                <h2>平日ダイヤ</h2><table><tr><td>07:05 07：35 8時10分</td></tr></table>
                <h2>土日祝ダイヤ</h2><table><tr><td>08:20 08:55</td></tr></table>
                """;

        OfficialTimetableParser.Timetable result = OfficialTimetableParser.parseHtml(page);

        assertEquals(List.of(LocalTime.of(7, 5), LocalTime.of(7, 35), LocalTime.of(8, 10)),
                result.weekdayTimes());
        assertEquals(List.of(LocalTime.of(8, 20), LocalTime.of(8, 55)), result.weekendTimes());
        assertEquals(List.of(LocalTime.of(8, 20), LocalTime.of(8, 55)), result.holidayTimes());
    }

    @Test public void parsesAnHourlyRowOutsideATable() {
        String page = "<h2>平日</h2><div>5時 05 35 50</div>";

        OfficialTimetableParser.Timetable result = OfficialTimetableParser.parseHtml(page);

        assertEquals(List.of(LocalTime.of(5, 5), LocalTime.of(5, 35), LocalTime.of(5, 50)),
                result.weekdayTimes());
    }

    @Test public void parsesSimplePublishedJsonSchedule() {
        String source = """
                {"weekdayTimes":["0705","07:35"],"weekendTimes":["08:10"],"holidayTimes":["09:20"]}
                """;

        OfficialTimetableParser.Timetable result = OfficialTimetableParser.parse(source, "application/json");

        assertEquals(List.of(LocalTime.of(7, 5), LocalTime.of(7, 35)), result.weekdayTimes());
        assertEquals(List.of(LocalTime.of(8, 10)), result.weekendTimes());
        assertEquals(List.of(LocalTime.of(9, 20)), result.holidayTimes());
    }

    @Test public void explicitLabelsTakePriorityBeforeAssigningUnnamedSectionsInBothFormats() {
        String page = """
                <table><tr><td>07:10</td></tr></table>
                <h2>平日</h2><table><tr><td>09:00</td></tr></table>
                <table><tr><td>08:20</td></tr></table>
                <table><tr><td>06:30</td></tr></table>
                """;
        String json = """
                {"first":["07:10"],"weekdayTimes":["09:00"],"second":["08:20"],"third":["06:30"]}
                """;

        OfficialTimetableParser.Timetable result = OfficialTimetableParser.parseHtml(page);

        assertEquals(result, OfficialTimetableParser.parseJson(json));
        assertEquals(List.of(LocalTime.of(6, 30), LocalTime.of(9, 0)), result.weekdayTimes());
        assertEquals(List.of(LocalTime.of(7, 10)), result.weekendTimes());
        assertEquals(List.of(LocalTime.of(8, 20)), result.holidayTimes());
    }

    @Test public void jsonFallbackStillSortsAndDeduplicatesAnUnnamedRootArray() {
        OfficialTimetableParser.Timetable result = OfficialTimetableParser.parseJson(
                "[\"07:35\",\"0705\",\"07:35\"]");

        assertEquals(List.of(LocalTime.of(7, 5), LocalTime.of(7, 35)), result.weekdayTimes());
        assertEquals(List.of(), result.weekendTimes());
        assertEquals(List.of(), result.holidayTimes());
    }
}
