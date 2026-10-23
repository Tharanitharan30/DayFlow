# Implementation Plan — Real Ringing Task Alarms in DayFlow (Android)

## Status: COMPLETE (All Steps Implemented & Verified)

## 1. Executive Summary & Architecture
This implementation brings **real, audible, Clock-style task alarms** to DayFlow. When a task's scheduled time arrives, it triggers an audible, looping alarm ringtone at the Android alarm audio stream (`AudioAttributes.USAGE_ALARM`), vibrates, and displays a dedicated, lock-screen-compatible full-screen alarm interface (`AlarmActivity`) with a **Slide-to-Dismiss** slider and a **Single-Tap Snooze** button.

### User Decisions Incorporated
- **Default Mode**: Tasks default to notification banners, with an explicit switch in the composer to elevate the task to a **Ringing Clock Alarm**.
- **Alarm Screen Gesture**: **Slide to Dismiss** to prevent accidental pocket dismissals, plus a prominent **Single-Tap Snooze** button.
- **Ringtone & Audio**: Uses the Android system default alarm ringtone (`RingtoneManager.TYPE_ALARM`) with tone preview in the task composer.

---

## 2. Technical Architecture & Components Implemented

### A. Data Layer & Room Migration (`DayFlowDatabase.kt`, `ReminderModels.kt`, `Task.kt`)
- Added alarm properties to `ReminderEntity` and `Task`:
  - `isRingingAlarm: Boolean = false`
  - `alarmSoundUri: String = "system"`
  - `alarmVibration: Boolean = true`
  - `snoozeDurationMinutes: Int = 10`
- Upgraded Room Database version to **4**:
  - Implemented `MIGRATION_3_4` adding new columns with safe default values for both `reminders` and `tasks` tables.
- Updated JSON Export/Import Schema in `ReminderRepository.kt` to round-trip these alarm properties.

### B. Background Audio & Alarm Playback Service (`AlarmService.kt`)
- Created an Android `Service` (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`):
  - Requests audio focus on `AudioAttributes.USAGE_ALARM`.
  - Plays looping alarm audio using `MediaPlayer` / `Ringtone` on the alarm audio stream so it rings even when media volume is muted.
  - Vibrates using `Vibrator` with an alternating alarm pattern (`0, 800, 400, 800, 400`).
  - Acquires a `PowerManager.WakeLock` (with auto-release timeout) to ensure CPU stays active while ringing.
  - Displays an ongoing high-priority notification with `fullScreenIntent` and action buttons (*Snooze*, *Dismiss*).

### C. Full-Screen Alarm Activity (`AlarmActivity.kt`)
- A dedicated Activity styled with DayFlow's design system:
  - Configured with `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, and `FLAG_KEEP_SCREEN_ON` on Android 8+ (API 27–36).
  - Displays:
    - Current digital time in large monospace typography with live seconds ticker (`hh:mm:ss a`).
    - Task Title, Description, and Category/Priority pills.
    - Pulsing alarm ring icon animation.
    - **Single-Tap Snooze Button** (snoozes for the configured interval, e.g. 5m, 10m, 15m).
    - **Slide-to-Dismiss Slider** (interactive Compose drag gesture requiring deliberate swipe to dismiss, preventing accidental pocket touches).
  - Stops the service on dismiss/snooze, cancels audio/vibration, and updates task state.

### D. Exact Alarm Scheduling (`ReminderScheduler.kt`)
- When `isRingingAlarm == true`:
  - Uses `AlarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent), firePendingIntent)`!
  - `setAlarmClock()` is the highest-priority, exact wall-clock alarm API on Android, guaranteed by the OS to fire on time, bypass Doze mode, and display the clock icon in the system status bar.
- When `isRingingAlarm == false`:
  - Continues using exact notification alarms (`setExactAndAllowWhileIdle`).
- Reschedules upon device reboot, time change, and timezone shifts via `BootAndTimeChangeReceiver`.

### E. Task Composer & Settings UI
- In `ReminderEditorSheet.kt`:
  - Under the "Notifications & Alerts" accordion, added a prominent **"Ringing Clock Alarm"** switch.
  - When enabled, displays:
    - **Alarm Sound Preview** button (play / stop sample ringtone).
    - **Snooze Duration** picker chips (5m, 10m, 15m).
    - **Vibrate** toggle.
- In `SettingsAndDiagnosticsScreen.kt` & `NotificationDiagnosticsScreen.kt`:
  - Added a **"Test Alarm"** button allowing the user to immediately trigger and test the full-screen ringing alarm screen.

### F. Permissions & Android Manifest (`AndroidManifest.xml`)
- Declared:
  - `<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />`
  - `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />`
  - `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />`
  - `<uses-permission android:name="android.permission.WAKE_LOCK" />`
  - `<uses-permission android:name="android.permission.VIBRATE" />`
  - `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />`
- Registered `AlarmService` and `AlarmActivity` in `AndroidManifest.xml` with `showOnLockScreen="true"`, `showWhenLocked="true"`, `turnScreenOn="true"`.

---

## 3. Verification & Testing Results

- **Unit & Robolectric Tests**: 9/9 tests passed (`BUILD SUCCESSFUL in 50s`).
  - `task with isRingingAlarm schedules AlarmClockInfo and supports snooze and dismiss`: PASSED.
  - `create edit complete snooze and delete one-time reminder`: PASSED.
  - `completing recurring reminder spawns next occurrence without duplicating`: PASSED.
  - `editing entire recurring series updates all active occurrences`: PASSED.
  - `timezone reconciliation recalculates epoch while preserving local scheduled date and time`: PASSED.
  - `json export and import preserves reminders tasks and user settings`: PASSED.
  - `Task entity TaskDao and TaskRepository persist fields filters and recurrence rules`: PASSED.
  - `app_name is DayFlow`: PASSED.
- **Compilation**: `compile_applet` passed cleanly with 0 errors.
