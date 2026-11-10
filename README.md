# Orbit — Personal Local-First Operating System for Android

[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)](#testing--verification)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%26%20Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Local--First-orange.svg)](#security--privacy)

Orbit is a personal, private, local-first operating system designed for Android. It combines daily task management, calendar context, a distraction-free video inbox, and private attention tracking with a floating manual Instagram Reel counter.

Orbit operates **100% offline with zero cloud dependency and no user account required**.

---

## 📸 Design System

Orbit features a bespoke OLED pitch-dark theme (`#0B0B0B`) with vivid warm sunset orange accents (`#FF793E` to `#FF914A`) and card surfaces (`#191919`, `#222222`):

- **Heading 1**: 32sp Bold
- **Heading 2**: 24sp SemiBold
- **Body**: 16sp Regular
- **Caption**: 12sp Medium
- **Primary Elements**: Pill-shaped action buttons, floating action buttons, statistic tiles, and card surfaces.

---

## 🚀 Key Modules & Capabilities

### 1. Today & Tasks Engine
- **Cockpit Overview**: Greeting, pending task metrics, and top priority *Next Task* card with one-tap completion.
- **Quick Metrics Grid**: 4 live metric cards tracking Completed, Pending, Overdue, and Focus Time.
- **Task Organization**: Live filter chips (*All*, *Today*, *Upcoming*, *Completed*), overdue task promotion, priority markers, and category tags.
- **Deterministic NLP Capture**: Offline natural-language date/time parsing (e.g. `"today"`, `"tomorrow 3pm"`, `"tonight"`, `"Friday"`, `"in 3 weeks"`, `"!urgent"`) with review-first protection that never loses the user's raw input.
- **Local Reminders**: Offline `AlarmManager`-backed task notifications.

### 2. Agenda & Calendar Timeline
- **Read-Only Calendar Sync**: Integrates with Android's system `CalendarContract` without modifying or deleting external events.
- **Combined Timeline**: Chronological 9:00 AM – 11:00 PM schedule combining local tasks with external meetings.
- **Horizontal Date Strip**: 7-day scrollable selector with active date indicators.
- **Meeting Notes**: Offline meeting preparation notes attached to stable calendar event snapshots.
- **Permission Fallback**: Graceful offline operation if calendar access is denied or revoked.

### 3. Distraction-Free Video Inbox (MyTube)
- **Share Target**: Ingests shared video URLs from Android share sheets.
- **Intelligent URL Parsing**: Normalizes YouTube URLs (standard, shortened, and playlist `list=` parameters) and extracts titles.
- **Anti-Algorithm Policy**: Zero algorithmic recommendations, zero infinite scroll, and zero autoplay.
- **External Player Dispatch**: Videos open strictly via explicit user intent in your preferred browser or video app.
- **Task Integration**: One-tap *Add to Task* workflow converting video items into scheduled study tasks.

### 4. Attention Dashboard & Draggable Floating Reel Counter
- **Honest Tracking**: Strictly distinguishes between **Manual Reel Count** (from append-only event logs) and **Instagram Foreground Time** (from Android `UsageStatsManager`).
- **Draggable Floating Pill**: Lightweight overlay (`WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`) appearing over Instagram with instant +1 increment, pause, and close controls.
- **Event-Sourced Persistence**: Monotonically ordered, immutable `CounterEvent`s preventing duplicate counts across app restarts.
- **Analytics**: Day, Week, and Month breakdowns with interactive weekly usage bar charts.

### 5. Notes & Meeting Notes
- Tagged personal notes (*Work*, *Ideas*, *Personal*, *College*), note pinning, search, and meeting snapshot attachments.

### 6. AI Multi-Tool Assistant
- **Provider-Independent Architecture**: Structured schema execution (`CreateTask`, `CreateNote`, `SearchNotes`, `QueryCalendar`, `SearchVideos`).
- **Review-First Validation**: Proposes actions in a review dialog—no data is written without explicit user confirmation.
- **Offline Fallback**: Operates entirely offline with deterministic local parsing and zero cloud leaks.

### 7. Security, Backup & Data Portability
- **Keystore Encryption**: Sensitive fields and exports encrypted using hardware-backed Android Keystore AES-256-GCM.
- **Integrity Validation**: SHA-256 checksums and foreign-key relation validation preventing corrupted or tampered imports.
- **Permanent Data Erasure**: Complete, one-tap "Erase All Data" control.

### 8. Orbit macOS Companion Daemon (`orbit-mac/`)
- **Hands-Free Ambient Voice**: Runs 24/7 in the background with zero terminal windows via macOS `LaunchAgent`.
- **Menu Bar Control (`🪐`)**: Top menu bar status indicator with fallback controls (Pause/Resume, Wake Now, Toggle Music, Live Logs, Quit).
- **Strict 5-Step WhatsApp Automation**: Searches contacts, opens chat, and sends text messages—or records voice notes with automatic 2.5-second silence detection.
- **Dynamic Spotify & YouTube Autoplay**: Searches and plays requested tracks on Spotify and YouTube without resuming old audio or getting stuck on search pages.
- **Window Display Switching**: Smoothly moves active windows between Mac displays and external monitors.
- **Zero Cost / Instant Offline Engine**: Deterministic local rule engine for instantaneous offline execution ($0 API cost), with automatic fallback to Groq Cloud LPU.

---

## 🏗️ Architecture & Tech Stack

Orbit is built on a clean, modular Android architecture:

```text
Orbit/
├── app/                  # Main Android shell, Hilt DI container, Navigation host, Settings
├── core-domain/          # Pure Kotlin domain entities, repository interfaces, use cases
├── core-data/            # Room database (v1 -> v2), DataStore, repository implementations, WorkManager
├── core-security/        # Android Keystore AES-256-GCM, SHA-256 checksums, input sanitizers
├── core-ui/              # Material 3 theme, tokens, and reusable atomic Compose components
├── core-ai/              # Offline NLP parser and provider-independent AI action layer
├── core-sync/            # Stubbed contracts for future opt-in E2E encrypted sync
├── feature-today/        # Tasks cockpit and natural-language capture
├── feature-agenda/       # Combined calendar timeline and meeting notes
├── feature-video-inbox/  # Distraction-free video queue and share target
├── feature-attention/    # Attention dashboard and floating WindowManager overlay
└── orbit-mac/            # Native macOS Jarvis executive voice assistant & menu bar daemon
```

- **Language**: Kotlin 1.9.22
- **UI Framework**: Jetpack Compose & Material 3
- **Dependency Injection**: Hilt
- **Local Persistence**: Room with asset-backed schema migration tests (`DatabaseMigrationTest`)
- **Key-Value Store**: Jetpack DataStore Preferences
- **Background Work**: WorkManager on-demand provider with `SessionRecoveryWorker` and `DailyRollupWorker`
- **Encryption**: Android Keystore AES-256-GCM & SHA-256

---

## 🧪 Testing & Verification

Orbit has a comprehensive test suite with **over 50 automated tests** passing across all modules:

- **`:core-data`**:
  - `DatabaseMigrationTest` (schema 1 -> 2 data preservation test)
  - `ReelCounterDaoTest` & `TaskDaoTest`
  - `ReelCounterRepositoryTest` (append-only log, monotonic sequences, multi-session reset)
  - `BackupRepositoryTest` (7-entity export/import, tampered checksum rejection, orphan checks)
- **`:app`**:
  - `OrbitComposeUiTest` (Robolectric Compose UI tests for navigation, dialogs, and components)
  - `AiAssistantViewModelTest`, `NotesViewModelTest`, `OnboardingViewModelTest`, `SettingsViewModelTest`
- **`:core-ai`**:
  - `NaturalLanguageDateParserTest` (relative dates, times, weekdays, bang priorities)
  - `OfflineDeterministicAiProviderTest` (structured schema creation & action validation)
- **`:feature-video-inbox`**:
  - `YouTubeUrlParserTest` (URL normalization, playlist parsing, share prefix cleaning)
  - `VideoInboxViewModelTest`
- **`:feature-agenda`**:
  - `AgendaViewModelTest` (date selection, timeline creation, permission fallback)
- **`:feature-today`**:
  - `TodayViewModelTest` (metric rollups, overdue task inclusion, filter states)
- **`:feature-attention`**:
  - `AttentionViewModelTest` (active session reuse, daily stats calculations)
- **`:core-security`**:
  - `SecurityTests` (AES-256-GCM encryption roundtrips, SHA-256 verifier, input sanitizer)
- **`:core-domain`**:
  - `BackupUseCasesTest`

---

## 🛠️ Building & Running

### Prerequisites
- JDK 17
- Android SDK 34+ (Build tools 34.0.0+)

### Commands

```bash
# Run all automated unit and Robolectric tests
./gradlew test

# Assemble the debug APK
./gradlew assembleDebug
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Security & Privacy Philosophy

1. **No Mandatory Account**: Orbit does not require an email, password, phone number, or cloud sign-in.
2. **Local by Default**: All tasks, calendar event snapshots, video URLs, counter logs, and notes are stored strictly on the local device.
3. **Transparent Permissions**: Every Android permission (`READ_CALENDAR`, `SYSTEM_ALERT_WINDOW`, `PACKAGE_USAGE_STATS`) is explained in-app before navigating to system settings.
4. **Zero Analytics / Telemetry**: No crash reporting, tracking SDKs, or analytics pings exist in the codebase.
5. **Encrypted Portability**: Exports are encrypted with Android Keystore-backed keys and verified using cryptographic checksums.

---

## 📄 License
This project is licensed under the Apache License 2.0.
