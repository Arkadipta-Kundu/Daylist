# Daylist 1.2 for Android
Tap + and type “Practice guitar tomorrow at 6 pm !!” followed by a quoted description and any number of [] checklist entries. Send saves immediately. Tap a preview chip or the paperclip to adjust the draft. Enter-to-send is optional; Shift+Enter adds a line. Date-only tasks remain silent. Explicit reminder times use the task date and override the notify-at-deadline setting. Times follow the phone’s local zone.

## Install and upgrade
Android 8+, current System WebView. Extract this project and put its contents, including .github, at the repository root. Push to run Build Android APK. Download and extract the Daylist-Android artifact. The workflow verifies the APK signature and prints the icon and package metadata.

Use the same signing key as your installed dev.daylist package. Different GitHub debug runs can use different keys. If Android rejects an update, preserve the original installation and its data until you have the matching signing key or a verified external backup. This project cannot recover an earlier runner's private key. For stable release builds, set repository Actions secrets DAYLIST_KEYSTORE_BASE64 (base64 of your existing keystore), DAYLIST_STORE_PASSWORD, DAYLIST_KEY_ALIAS and DAYLIST_KEY_PASSWORD. With no keystore configured, the workflow builds a debug APK. Locally use Gradle 8.9, JDK 17, Platform 35 and build tools 35.0.0.

## Cloud account setup
1. In Google Cloud Console create/select a project and enable Google Drive API.
2. In Google Auth Platform configure Branding and Audience. For a personal Gmail account choose External; in Testing add your own email as a test user. Under Data Access request only https://www.googleapis.com/auth/drive.appdata. Review and publish the consent configuration before distributing beyond testers.
3. Create an OAuth client of type Android, package dev.daylist. Register the SHA-1 of the certificate actually signing the installed APK. The workflow prints this using apksigner. Create separate Android clients for distinct signing certificates, including the Play app signing certificate if distributed there. No client secret or service account belongs in the APK.
4. On an Android device with Google Play services, open Settings → Connect Drive and grant access. The active Google account owns these private snapshots. Choose the same account when restoring. Background work requires network and may be deferred. Failed authorization needs foreground reconnection.
5. Create a test task, tap Back up now and wait for Verified. Open Restore from Drive, select a snapshot, check the count and confirm replacement on a test installation. Export a separate copy using the system file picker before testing a restore on a populated device.

## Data and recovery
Preserves the existing task store and identifiers. Snapshots carry the complete task records, deadline notification and keyboard preferences, schema version, creation timestamp and a SHA-256 integrity check. (The checksum detects corruption and is not an authentication or encryption mechanism.) Exported JSON contains readable task data. Cloud snapshots use HTTPS, read-back verification and five-version retention. Empty collections are skipped during automatic backup. Restore replaces local data only after validation and confirmation; it rebuilds reminders and clears prior task notifications. Android system backup includes only the tasks preference file. Its timing and availability depend on the device. Cross-device simultaneous editing is outside this backup model.

## Reminders and verification
AlarmManager schedules the next eligible wake-up. Android's persisted repair job and receivers reconcile on startup, reboot after unlock, time/zone changes and updates. Exact access denial uses an inexact wake-up. Force-stopping requires reopening the app. A missed interval produces the latest eligible alert per task. Complete and Snooze (10 minutes) run through the receiver. Notification delivery and sound depend on device permissions, channel settings and system limits.

Run node tests/capture.cjs. Before trusting alerts or cloud recovery, use disposable test data: check locked-screen delivery, snooze, system process kill, reboot, time changes, multiple simultaneous tasks, edited/cancelled reminders, permission denial, offline changes followed by reconnection, failed uploads, corrupted imports and reinstall restore. Check the launcher icon in Settings, Recents and themed icon mode. Android build, installation, Drive authorization and phone delivery require external verification.


## Native interface and visual checks
The activity hosts a Material 3 Compose interface. The packaged parser runs locally in an isolated JavaScript engine backed by System WebView. Markdown preview uses a separate read-only WebView with JavaScript and network access disabled. Task storage, IDs, reminder channels and backup schema are compatible with version 1.1. Open the overflow menu for Completed or Settings.

The GitHub workflow includes a separate emulator job. It captures the task list, composer, metadata sheet, task editor, completed list and settings in a normal light profile and a compact dark profile with 150% text. Download the Daylist-UI-checks artifact to inspect the images. These are instrumented captures from the built app. To run on an attached disposable emulator locally, run bash tests/visual.sh from the project root. The script clears Daylist and changes emulator display settings between runs.
