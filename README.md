# DayFlow ⏰✨

> **A modern, reliable Android Task Planner and Clock-Style Ringing Alarm System.**  
> Built with 100% Kotlin, Jetpack Compose, Room Database, and native Android `AlarmManager`.

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![MinSdk](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![TargetSdk](https://img.shields.io/badge/Target%20SDK-36-blue.svg)](https://developer.android.com)

---

## 📖 Overview

Most todo and task apps rely on silent or easy-to-miss notification banners. **DayFlow** bridges the gap between task management and wake-up alarm clocks: critical tasks can trigger a **full-screen, audible, clock-style ringing alarm** that continues ringing over the lock screen until you actively snooze or slide to dismiss it.

DayFlow leverages Android's high-priority `AlarmManager.setAlarmClock()` API, bypassing Doze mode restrictions, and survives device reboots and app termination.

---

## 🌟 Key Features

### 1. 🔔 Real Clock-Style Ringing Alarms
- **Audible Ringtone Playback**: Real looped alarm audio played on the `STREAM_ALARM` audio stream via a dedicated `AlarmAudioPlayer` service with proper audio focus management.
- **Full-Screen Lock-Screen UI**: Wakes the device and appears over the lock screen (`AlarmActivity`) when scheduled, showing the task title, category, priority, and current time.
- **Slide to Dismiss & Single-Tap Snooze**: Designed to prevent accidental dismissal with a smooth interactive slide-to-dismiss threshold and a quick 10-minute snooze button.
- **Custom Sound & Vibration**: Toggle vibration, preview tones, and choose between notification banners or audible ringing alarms per task.

### 2. 📅 Comprehensive Task & Schedule Management
- **Recurring Schedules**: Flexible recurrences including Daily, Weekdays, Weekly (custom weekday multi-selection), Monthly, and Yearly.
- **Recurrence Controls**: End dates, max occurrences, and pause/resume toggles without losing original task definitions.
- **Categorization & Priorities**: Organize by Work, Personal, Health, Urgent, or Custom categories with visual priority badges (Low, Medium, High, Urgent).
- **Search & Quick Filters**: Filter by today's agenda, upcoming tasks, completed history, or category tabs.

### 3. 🛡️ Rock-Solid Background Reliability
- **`AlarmManager.setAlarmClock()`**: Utilizes exact alarm APIs (`RTC_WAKEUP`) to guarantee time-critical firing.
- **Reboot Persistence**: `BootReceiver` automatically queries Room and reschedules all pending alarms immediately after device reboot (`ACTION_BOOT_COMPLETED`).
- **Time Zone & Clock Changes**: Reschedules pending alarms on `TIME_SET` and `TIMEZONE_CHANGED` system broadcasts.
- **Process Termination Safe**: Alarms trigger independently even if the app process has been cleared or terminated by Android.

### 4. 📊 Ringing History & Battery Diagnostics
- **Ringing History Log**: Tracks the last 10 triggered alarms, recording:
  - Scheduled epoch timestamp
  - Actual broadcast delivery timestamp
  - Delay delta (in seconds)
  - Severity classification: **On Time** (≤ 15s), **Minor Delay** (16s – 180s), or **Battery Delay** (> 180s).
- **OEM Battery Delay Detection**: Identifies aggressive manufacturer battery savers (e.g. Xiaomi MIUI/HyperOS, Samsung One UI, Huawei, OnePlus) and advises users to whitelist DayFlow from battery optimization.
- **Interactive Test Alarm**: One-tap diagnostic button to immediately trigger a test alarm and verify audio, vibration, and broadcast reception.

---

## 🏗️ Architecture & Technology Stack

DayFlow follows **Clean Architecture** and the official Android **MVVM** pattern:

```
app/src/main/java/com/example/
├── MainActivity.kt               # Main Jetpack Compose entry point
├── DayFlowApp.kt                 # Top-level navigation scaffold & screen routing
├── data/                         # Local persistence layer
│   ├── Task.kt                   # Task Room entity, categories, and priority enums
│   ├── TaskDao.kt                # SQLite queries for tasks and recurrence rules
│   ├── ReminderEntity.kt         # Backward-compatible reminder entity
│   ├── AlarmHistoryModels.kt     # AlarmHistoryEntity, DAO, and DelaySeverity enum
│   ├── DayFlowDatabase.kt        # Room database with migrations (MIGRATION_1_2 through 4_5)
│   ├── ReminderScheduler.kt      # AlarmManager exact scheduling & cancellation
│   └── UserPreferencesRepository # DataStore-backed theme, snooze, and sound preferences
├── ui/                           # Jetpack Compose UI
│   ├── AlarmActivity.kt          # Dedicated full-screen ringing alarm activity
│   ├── AlarmAudioPlayer.kt       # Looped audio playback, audio focus, & vibrator engine
│   ├── NotificationDiagnosticsScreen.kt # System status, OEM guides, & Ringing History tab
│   ├── TaskScreens.kt            # Task list, agenda, and creation/editing bottom sheets
│   └── theme/                    # Material 3 ColorScheme, Typography, and Shapes
└── receiver/                     # Android BroadcastReceivers
    ├── ReminderAlarmReceiver.kt  # AlarmManager trigger handler & history logging
    └── BootReceiver.kt           # Device boot & time-change alarm restoration
```

- **UI Framework**: Jetpack Compose with Material Design 3 (M3)
- **Local Database**: Room 2.7.0-alpha13 (SQLite with automated schema migrations)
- **Concurrency**: Kotlin Coroutines & Reactive Flows
- **Testing**: Robolectric 4.14.1 with JVM ShadowAlarmManager tests

---

## 🔐 Required Android Permissions

DayFlow declares and handles standard Android permissions in `AndroidManifest.xml`:

| Permission | Purpose |
| :--- | :--- |
| `android.permission.SCHEDULE_EXACT_ALARM` | Required on Android 12+ (API 31+) to schedule exact alarm clocks |
| `android.permission.USE_FULL_SCREEN_INTENT` | Displays the ringing alarm screen over the lock screen on Android 14+ |
| `android.permission.POST_NOTIFICATIONS` | Required on Android 13+ (API 33+) to display alarm and task alerts |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Restores pending alarms after the device reboots |
| `android.permission.FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Reliable background audio playback while ringing |
| `android.permission.VIBRATE` | Haptic vibration during ringing alarms |
| `android.permission.WAKE_LOCK` | Keeps the screen on during active alarms |

> **Tip for OEM Devices**: For devices running Xiaomi MIUI/HyperOS, Samsung One UI, Oppo/Realme ColorOS, or Vivo FunTouch, ensure **Autostart** is enabled and **Battery Saver** is set to *No Restrictions* in the device's App Info settings.

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio** Ladybug (2024.2.1) or newer
- **JDK 17** or JDK 21
- **Android SDK Platform 36** (Min SDK 26 / Android 8.0 Oreo)

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/dayflow.git
   cd dayflow
   ```

2. Build debug APK:
   ```bash
   gradle assembleDebug
   ```

3. Run the unit & Robolectric test suite:
   ```bash
   gradle testDebugUnitTest
   ```

4. Install the APK to a connected Android device or emulator:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 🧪 Testing

DayFlow includes a comprehensive Robolectric unit test suite covering:
- Exact alarm scheduling and cancellation with `ShadowAlarmManager`.
- Snooze calculation and 10-minute postponement logic.
- Task recurrence rule calculation (Daily, Weekdays, Custom Weekday sets, Monthly, Yearly).
- Room database migrations (`MIGRATION_1_2` through `MIGRATION_4_5`).
- Alarm history tracking, actual vs. scheduled delay calculation, and table pruning to the last 10 entries.

Run tests anytime with:
```bash
gradle :app:testDebugUnitTest
```

---

## 📄 License

This project is licensed under the Apache 2.0 License. See the [LICENSE](LICENSE) file for details.
