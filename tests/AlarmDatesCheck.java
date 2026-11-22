package dev.daylist;
import java.time.LocalDate;

public class AlarmDatesCheck {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        check(AlarmDates.resolve("", "", false) == null);
        AlarmDates inbox=AlarmDates.resolve("", "2026-12-25", false);
        check(inbox.start.equals(LocalDate.parse("2026-12-25")) && inbox.offset==0);
        AlarmDates old=AlarmDates.resolve("2026-12-20", "", true);
        check(old.start.equals(LocalDate.parse("2026-12-20")) && old.offset==0);
        AlarmDates early=AlarmDates.resolve("2027-01-01","2026-12-31",true);
        check(early.offset==-1 && early.start.plusWeeks(1).plusDays(early.offset).equals(LocalDate.parse("2027-01-07")));
        AlarmDates later=AlarmDates.resolve("2026-12-24","2026-12-25",false);
        check(later.start.equals(inbox.start));
        System.out.println("Alarm date checks passed.");
    }
}
