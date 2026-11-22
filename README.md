# Daylist 1.4 for Android
Offline Material 3 tasks with a Today widget, recurring schedules, Markdown, reminders and manual JSON transfer.

## Quick capture
| Feature | Example |
|---|---|
| Inbox | Buy milk |
| Today | Study Java today |
| Tomorrow | Call Rahul tomorrow |
| Deadline | Finish report tomorrow by 5 pm |
| Reminder | Call mom remind at 8 pm |
| Specific date | Pay bill remind at 25 December 2026 10 am |
| Relative reminder | Take medicine remind in 30 minutes |
| Priority | Buy groceries ! / Finish project !! / Submit assignment !!! |
| Description | Study Java "Revise Collections" |
| Subtasks | Clean room () Organize desk () Sweep floor |
| Tags | Study SQL #learning |
| Recurrence | Workout every Monday |

The reminder and deadline have separate dates. A time-only reminder selects its next occurrence and leaves an otherwise undated task in Inbox. Explicit past dates and missing times require correction. Tap the reminder chip to edit. On recurring tasks the reminder keeps its day offset relative to each scheduled occurrence. The explicit reminder overrides the optional automatic deadline notification. The parser runs locally. Parentheses containing text and code spans remain literal. Quoted Markdown descriptions retain their checklist formatting.

## Data and upgrade
Existing task IDs, local preference storage and manual JSON format are retained. For reminders saved by earlier releases without a reminder date, Daylist uses the existing due date. Settings → Data management provides JSON export and import. Import validates the snapshot before offering to replace local tasks. There is no online account or automatic backup. Export a file outside the app before uninstalling. Updates require the same signing certificate as the installed package.

## Home screen and notifications
Long press the home screen → Widgets → Daylist · Today. Tap the checkbox to complete a task, its title to edit it, the heading to open Today or + to capture a task. The scrollable list includes overdue tasks. A recurring task advances once per occurrence. The phone may defer midnight widget refreshes during sleep. Give Daylist notification and exact-alarm access where available. Android may delay inexact alarms. Restart recovery runs after first unlock, and force-stop requires reopening Daylist.

## Build
Extract the project at the repository root including .github. Push to run Build Android APK. Download Daylist-Android from the apk job for the installable build. UI test artifacts are separate. The standard job builds debug unless all release signing secrets are configured. Reuse your prior DAYLIST_KEYSTORE_BASE64, DAYLIST_STORE_PASSWORD, DAYLIST_KEY_ALIAS and DAYLIST_KEY_PASSWORD secrets for a release upgrade. Local build requires JDK 17, Gradle 8.9 and SDK Platform 35; run gradle :app:testDebugUnitTest :app:assembleDebug.

## Performance and tests
The system splash has light/dark launch resources. Task reads and sorting, writes, and reminder recovery run off the main thread. The app loads its parser on composer demand and initializes Markdown rendering on use. There is no startup synchronization worker. The widget and Today screen share grouping and completion logic.
Run node tests/capture.cjs for parser regressions. The CI emulator suite covers UI capture, local task export validation, notification cancellation and idempotent widget completion. To compare the previous 1.2 build against this version on a dedicated Android 12+ physical device, run bash tests/compare-performance.sh /path/to/extracted/1.2 ./performance-results. The harness uses a separate profileable package and 500 disposable tasks. Compare startup, frame timing and interaction traces under the same device conditions. The previous build lacks the task-ready marker, so use initial-display timing for the direct startup comparison. No measured performance figures or physical-device results are included.
