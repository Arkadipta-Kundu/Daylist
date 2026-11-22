package dev.daylist;

import java.time.LocalDate;

/** Relates a task occurrence to its independently selected reminder day. */
final class AlarmDates {
    final LocalDate start;
    final long offset;

    private AlarmDates(LocalDate start, LocalDate reminder) {
        this.start = start;
        this.offset = reminder.toEpochDay() - start.toEpochDay();
    }

    static AlarmDates resolve(String due, String explicitDay, boolean recurring) {
        String selected = explicitDay.isEmpty() ? due : explicitDay;
        if (selected.isEmpty()) return null;
        LocalDate first = LocalDate.parse(selected);
        return new AlarmDates(recurring && !due.isEmpty() ? LocalDate.parse(due) : first, first);
    }
}
