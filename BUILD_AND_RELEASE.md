# DayFlow — Android Build, Installation & Release Signing Guide

DayFlow is a native Android personal reminder and daily planner application built with **Kotlin**, **Jetpack Compose (Material 3)**, **Room SQLite Database**, **DataStore Preferences**, and **Android AlarmManager / BroadcastReceivers**.

---

## 1. Building the Installable Debug APK

To compile the debug APK locally or in CI:

```bash
gradle assembleDebug
```

The generated installable APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Installing via ADB on a Physical Android Phone

1. Enable **Developer options** and **USB debugging** on your Android device.
2. Connect the phone via USB and verify connection:
   ```bash
   adb devices
   ```
3. Install the debug APK:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## 2. Running Automated Unit & Robolectric QA Tests

DayFlow includes comprehensive JVM & Robolectric tests verifying CRUD operations, recurring reminder advancement, timezone/clock reconciliation, alarm scheduling, and UI interactions:

```bash
gradle :app:testDebugUnitTest
```

---

## 3. Producing a Signed Release APK / App Bundle

1. **Generate an Upload Keystore** (if you do not already have one):
   ```bash
   keytool -genkey -v -keystore my-upload-key.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias upload
   ```
2. **Provide Signing Credentials via Environment Variables**:
   ```bash
   export KEYSTORE_PATH="/absolute/path/to/my-upload-key.jks"
   export STORE_PASSWORD="your_keystore_password"
   export KEY_PASSWORD="your_key_password"
   ```
3. **Build the Signed Release APK**:
   ```bash
   gradle assembleRelease
   ```
   Output location:
   ```text
   app/build/outputs/apk/release/app-release.apk
   ```

---

## 4. Notification Reliability & Android Power Management Notes

- **Android 13+ (`POST_NOTIFICATIONS`)**: DayFlow explains notification permissions during onboarding and provides a one-tap permission request in **Settings → Notification Diagnostics**.
- **Exact Alarms (`SCHEDULE_EXACT_ALARM`)**: DayFlow uses `AlarmManager.setExactAndAllowWhileIdle` when allowed and gracefully falls back to `AlarmManager.setAndAllowWhileIdle` if exact alarm permission is revoked.
- **Reboot & Timezone Recovery**: `BootAndTimeChangeReceiver` listens for `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, and `TIMEZONE_CHANGED` to reconcile stored local dates/times against the active timezone and reschedule all future alarms.
- **Platform Limitations**: Manually choosing **Force Stop** in Android system settings cancels all OS PendingIntents until the user next opens DayFlow. Aggressive OEM battery savers (Samsung Deep Sleep, Xiaomi MIUI Autostart restrictions, etc.) are documented inside the in-app **Notification Diagnostics** screen.
