# DayFlow — Complete Material 3 UI/UX Redesign, Notification Diagnostics & Full Data Backup

Redesign the DayFlow Android productivity app with a refined Material Design 3 interface inspired by Todoist, TickTick, and Google Calendar, while adding a dedicated Notification Diagnostics center and full JSON export/import for reminders, tasks, and settings.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following design and UX decisions were confirmed during our interactive planning interview and govern the redesign across all screens:

- **Confirmed Decision 1 — Visual Palette & Mood**: **Warm Indigo and Emerald minimal productivity palette**, combining a crisp royal-indigo primary accent (`#4F46E5` Light / `#818CF8` Dark), an emerald completion/habit accent (`#10B981` Light / `#34D399` Dark), warm neutral alabaster/slate surfaces, and semantic priority tokens.
- **Confirmed Decision 2 — Task & Reminder Card Density**: **Compact scannable cards with inline action pills and swipe gestures** (`SwipeToDismissBox` for quick right-swipe completion and left-swipe snooze/reschedule), animated spring checkboxes, subtle 1dp surface borders, and expandable metadata pills.
- **Confirmed Decision 3 — Home Dashboard Layout**: **Progress ring summary with 24-hour upcoming timeline strip and grouped sections** (*Overdue*, *Today's Schedule*, *Upcoming*, and collapsible *Completed*), paired with an inline quick-capture bar and floating action button.

---

## 1. Overview & Core Concept

- **What It Does**: DayFlow is an offline-first native Android reminder, recurring habit tracker, and daily planner. This redesign elevates every screen with a cohesive Material 3 design language, micro-animations, swipe gestures, a dedicated Notification Diagnostics screen, and portable JSON backup/restore for both reminders and user preferences.
- **Target Audience / Persona**: Busy professionals, students, and daily planners on Android smartphones who want a calm, distraction-free task manager with rock-solid native local notifications.
- **Key Value**: Combines effortless one-handed task capture and scannable visual hierarchy with transparent Android notification health diagnostics and zero-lock-in local SQLite + JSON data portability.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Home Dashboard Flow**: Users open DayFlow to a time-of-day greeting, today's date, and a live Notification Health pill. A refined Progress Hero Card displays an animated circular completion ring alongside today's completed/pending metrics and an urgent-task callout. Below, a horizontal 24-hour Upcoming Timeline strip previews imminent events, followed by an inline quick-add bar and grouped task sections (*Overdue*, *Today*, *Upcoming*, *Completed*).
2. **Task Interaction & Swipe Flow**: Each task renders as a compact, scannable card with a priority-colored animated completion checkbox, title, optional single-line note preview, and inline pills for due time, category, and recurrence schedule. Users can tap the checkbox (with scale/color spring animation), swipe right to complete/uncomplete, swipe left to snooze (+10m), or tap the card to open the full Create/Edit sheet.
3. **Tasks Explorer Flow**: Dedicated search bar with instant filtering across title, description, and category; horizontal filter chips for status (*All*, *Active*, *Overdue*, *Today*, *Recurring*, *Paused*, *Completed*), priority, and category; and a sort selector (*Scheduled Time*, *Priority*, *Created Date*).
4. **Calendar Multi-View Flow**: Segmented view switcher for **Month** (compact 7-column grid with multi-dot category indicators), **Week** (7-day interactive ribbon), **Day** (hourly time-block schedule), and **Agenda** (chronological grouped timeline).
5. **Create / Edit Task Sheet Flow**: Streamlined bottom sheet with clear section cards for Title & Notes, Date & Time (quick-select chips + Material 3 DatePicker/TimePicker dialogs), Priority & Category swatches, Recurrence Builder (daily, selected weekdays, weekly, monthly, yearly, custom interval, end date, max occurrences, pause toggle), and Multi-Alert Notification timing. Editing a recurring task prompts whether to apply changes to *This Occurrence Only* or the *Entire Series*.
6. **Notification Diagnostics Flow**: Accessible from both the Home header badge and Settings. Displays live permission status (`POST_NOTIFICATIONS`), exact-alarm capability (`SCHEDULE_EXACT_ALARM`), notification channel state, battery optimization status, interactive manufacturer-specific (Samsung, Xiaomi, OnePlus/Oppo, Huawei, Pixel) battery guidance, direct deep-links to Android system settings, and one-tap **Test Alert** / **Reschedule All** actions.
7. **Full Data Export & Import Flow**: In Settings, users can export all reminders, tasks, and app preferences to a `.json` file via Android's Storage Access Framework (`CreateDocument`), import a `.json` backup file (`OpenDocument`), or inspect/copy/paste raw JSON in an interactive backup modal with an option to merge or replace existing records.

### Visual Identity & Theme
- **Aesthetic Direction**: Modern Editorial Minimalism (inspired by Todoist, TickTick, and Google Calendar Material You).
- **Color Palette & Mood**:
  - *Light Mode*: Background `#F8FAFC` (Cool Alabaster), Surface `#FFFFFF`, Surface Variant `#F1F5F9`, Primary `#4F46E5` (Warm Royal Indigo), Secondary `#10B981` (Emerald Mint), Tertiary `#0EA5E9` (Sky Cyan), High Priority `#E11D48` (Rose), Medium Priority `#D97706` (Amber), Low Priority `#0D9488` (Teal), Subtle Border `#E2E8F0`.
  - *Dark Mode*: Background `#0B0F19` (Deep Obsidian Slate), Surface `#111827`, Surface Variant `#1E293B`, Primary `#818CF8` (Soft Indigo), Secondary `#34D399` (Luminous Emerald), Tertiary `#38BDF8` (Sky), Subtle Border `#1F2937`.
- **Typography & Hierarchy**: Bundled Google Fonts pairing **Plus Jakarta Sans** (Bold/SemiBold headings and clean body copy) with **JetBrains Mono** (tabular timestamps, recurrence counters, and diagnostic metrics), scaled using `sp` for accessibility.
- **Component Styling & Layout**: 8dp spatial grid, 16dp–24dp card corner radii, 1dp subtle tonal borders instead of heavy drop shadows, and minimum 48dp touch targets on all interactive controls.
- **Interactive Feedback & Motion**: Spring-animated checkbox completion, `SwipeToDismissBox` background color/icon reveals, smooth `AnimatedVisibility` section collapses, and confirmation dialogs before destructive deletes.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Reliable Swipe Gestures with Non-Destructive Defaults**:
  - *Chosen Approach*: Right-swipe toggles task completion; left-swipe snoozes an active task by the user's default snooze interval (10m) and snaps back rather than silently deleting the task.
  - *Why*: Accidental swipe deletion is frustrating on mobile screens; destructive deletion remains safely guarded behind a confirmation dialog inside the task editor.
  - *Alternatives Considered*: Swipe-to-delete without confirmation (rejected due to accidental data loss risk).
- **Decision 2 — Dedicated Full-Screen Notification Diagnostics View**:
  - *Chosen Approach*: Promote Notification Diagnostics to a dedicated full-screen destination (reachable from both the Home status pill and the Settings tab) with deep-link intents to OS Notification Settings, Exact Alarm Settings (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`), Battery Optimization Settings, and App Info.
  - *Why*: Gives users clear explanations and one-tap remediation when Android 13+ permissions, Android 12+ exact alarms, or OEM battery restrictions affect background delivery.
- **Decision 3 — Unified JSON Backup Schema v3 (Reminders + Tasks + Settings)**:
  - *Chosen Approach*: Include `"settings"`, `"reminders"`, and `"tasks"` in a single human-readable JSON file and automatically reschedule all imported active reminders through `AlarmManager`.
  - *Why*: Ensures users migrating devices or restoring backups recover both their scheduled reminders and their custom preferences in one step.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram
```text
┌────────────────────────────────────────────────────────────────────────────┐
│                        MainActivity & DayFlowTheme                         │
│        (Warm Indigo & Emerald M3 Palette • Plus Jakarta Sans + Mono)       │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                     DayFlowApp (Adaptive Navigation Shell)                 │
│   Bottom NavigationBar (Compact) / NavigationRail (Expanded) + FAB + SAF   │
├────────────────┬────────────────┬────────────────┬─────────────────────────┤
│ Home Dashboard │ Calendar View  │ All Tasks View │ Settings & Diagnostics  │
│ • Progress Ring│ • Month Grid   │ • Search Bar   │ • Notification Health   │
│ • 24h Timeline │ • Week Ribbon  │ • Status/Prio/ │   Full-Screen View      │
│ • Quick-Add Bar│ • Day Hourly   │   Category Chip│ • Theme/Time/Defaults   │
│ • Grouped Lists│ • Agenda List  │ • Sort Menu    │ • JSON Export & Import  │
└────────┬───────┴────────┬───────┴────────┬───────┴────────────┬────────────┘
         │                │                │                    │
         └────────────────┴────────┬───────┴────────────────────┘
                                   ▼
┌────────────────────────────────────────────────────────────────────────────┐
│             Reusable Design System & Interactive Modals                    │
│ • SwipeableReminderCard  • ReminderEditorSheet  • JsonBackupRestoreDialog  │
│ • OnboardingDialog       • Priority/Category Pills & EmptyState Cards      │
└──────────────────────────────────┬─────────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                  DayFlowViewModel (Unidirectional StateFlow)               │
├──────────────────────────────────┬─────────────────────────────────────────┤
│     ReminderRepository &         │         ReminderScheduler               │
│   UserPreferencesRepository      │  (AlarmManager + NotificationCompat)    │
│ • Room SQLite (reminders, tasks) │ • Exact / Idle-Aware RTC_WAKEUP Alarms  │
│ • DataStore Preferences          │ • ReminderAlarmReceiver (Snooze/Done)   │
│ • JSON Export / Import Engine    │ • BootAndTimeChangeReceiver Recovery    │
└──────────────────────────────────┴─────────────────────────────────────────┘
```

### Data Model & State
- **`ReminderEntity` & `Task` (Room SQLite v3)**: Stores title, description, timezone-independent `localDateStr` (`YYYY-MM-DD`) and `localTimeStr` (`HH:mm`), computed `scheduledEpochMillis`, category, color hex, priority (`HIGH`, `MEDIUM`, `LOW`), recurrence rules (`NONE`, `DAILY`, `SELECTED_WEEKDAYS`, `WEEKLY`, `MONTHLY`, `YEARLY`, `CUSTOM_DAYS`, interval, weekdays, end date, max occurrences, paused flag), advance alert offsets, snooze timestamp, and completion state.
- **`UserSettings` (Jetpack DataStore)**: Persists theme mode (`SYSTEM`, `LIGHT`, `DARK`), 24-hour clock format, week-start day, default priority/category/advance alert/snooze minutes, sound/vibration toggles, and last alarm reconciliation metadata.
- **`DayFlowUiState`**: Single immutable `StateFlow` combining reminders, user settings, active tab/sub-screen (`showDiagnosticsScreen`), calendar view mode, search/filter/sort criteria, editor sheet state, and live `NotificationDiagnostics`.

### Interactive Component & State Mapping
- **Home Dashboard & Swipeable Cards**:
  - Tapping the top Notification Health badge or Settings diagnostics card transitions to the full `NotificationDiagnosticsScreen` (`BackHandler` returns to the previous screen).
  - Swiping right on `ReminderCard` invokes `viewModel.toggleCompleted(reminder)`, triggering a spring completion animation, cancelling pending alarms, and spawning the next recurring occurrence if applicable.
  - Swiping left on an uncompleted `ReminderCard` invokes `viewModel.snoozeReminder(reminder, defaultSnoozeMinutes)`, rescheduling the `AlarmManager` trigger and showing a confirmation snackbar.
- **Create / Edit Sheet**:
  - Validates non-empty title with inline error helper text, updates date/time via M3 pickers or 1-tap preset chips, and persists either single-occurrence or entire-series edits via `viewModel.saveReminder(reminder, editScope)`.
- **Notification Diagnostics Screen**:
  - Evaluates `POST_NOTIFICATIONS`, `AlarmManager.canScheduleExactAlarms()`, notification channel importance, and `PowerManager.isIgnoringBatteryOptimizations()`.
  - Launches targeted Android system settings intents (`ACTION_APP_NOTIFICATION_SETTINGS`, `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, `ACTION_APPLICATION_DETAILS_SETTINGS`) and refreshes diagnostics automatically on `Lifecycle.Event.ON_RESUME`.
- **JSON Data Export & Import**:
  - Exports the full JSON bundle (schema v3 with `"settings"`, `"reminders"`, and `"tasks"`) to a user-selected file via `ActivityResultContracts.CreateDocument("application/json")` or the in-app JSON inspector dialog.
  - Imports from a `.json` file via `ActivityResultContracts.OpenDocument()` or pasted text, restoring preferences into DataStore, inserting records into Room, and reconciling all `AlarmManager` triggers.
