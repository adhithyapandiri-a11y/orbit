# Orbit System Architecture & Specifications

## 1. Proposed Project Tree
```text
Orbit/
├── app/
│   ├── src/main/java/com/orbit/app/
│   │   ├── OrbitApp.kt
│   │   ├── MainActivity.kt
│   │   ├── navigation/
│   │   │   ├── OrbitNavHost.kt
│   │   │   └── Screen.kt
│   │   └── ui/
│   │       └── SettingsScreen.kt
│   ├── src/main/res/
│   └── src/main/AndroidManifest.xml
├── core-domain/
│   └── src/main/java/com/orbit/core/domain/
│       ├── model/
│       │   ├── Task.kt
│       │   ├── CalendarEvent.kt
│       │   ├── MeetingNote.kt
│       │   ├── VideoItem.kt
│       │   ├── ReelCounter.kt
│       │   ├── Preferences.kt
│       │   └── Backup.kt
│       ├── repository/
│       │   ├── TaskRepository.kt
│       │   ├── CalendarRepository.kt
│       │   ├── MeetingNoteRepository.kt
│       │   ├── VideoInboxRepository.kt
│       │   ├── ReelCounterRepository.kt
│       │   ├── PreferencesRepository.kt
│       │   └── BackupRepository.kt
│       └── usecase/
│           ├── ExportDataUseCase.kt
│           ├── ImportDataUseCase.kt
│           ├── EraseAllDataUseCase.kt
│           ├── GetPreferencesUseCase.kt
│           └── UpdatePreferencesUseCase.kt
├── core-security/
│   └── src/main/java/com/orbit/core/security/
│       ├── CryptoManager.kt
│       ├── ChecksumVerifier.kt
│       └── InputSanitizer.kt
├── core-data/
│   ├── schemas/
│   └── src/main/java/com/orbit/core/data/
│       ├── db/
│       │   ├── OrbitDatabase.kt
│       │   ├── entity/
│       │   ├── dao/
│       │   ├── converter/
│       │   └── migration/
│       ├── datastore/
│       ├── repository/
│       ├── worker/
│       └── di/
├── core-ui/
│   └── src/main/java/com/orbit/core/ui/
│       ├── theme/
│       └── component/
├── core-ai/
│   └── src/main/java/com/orbit/core/ai/
│       └── parser/
├── core-sync/
│   └── src/main/java/com/orbit/core/sync/
├── feature-today/
├── feature-agenda/
├── feature-video-inbox/
└── feature-attention/
```

## 2. Gradle Module Dependencies
- **core-domain**: Base domain contracts (Kotlin, Coroutines, Serialization).
- **core-security**: Security and sanitization utilities -> depends on `core-domain`.
- **core-data**: Database, DataStore, Repositories, Workers -> depends on `core-domain`, `core-security`.
- **core-ui**: Theme, Design System, Shared Components -> depends on `core-domain`.
- **core-ai**: Offline deterministic date parser -> depends on `core-domain`.
- **core-sync**: Encrypted sync stub -> depends on `core-domain`.
- **feature-today**: Task management UI -> depends on `core-domain`, `core-ui`, `core-ai`.
- **feature-agenda**: Agenda timeline UI -> depends on `core-domain`, `core-ui`.
- **feature-video-inbox**: Video inbox UI -> depends on `core-domain`, `core-ui`, `core-security`.
- **feature-attention**: Private usage & reel counter UI -> depends on `core-domain`, `core-ui`.
- **app**: Orchestrator, Shell, Hilt DI, Main UI -> depends on all modules.

## 3. Room Schema (Version 1)
- `tasks`: UUID id, title, notes, due_date_epoch_day, due_time_minute_of_day, priority, is_completed, completed_at, reminder_at, created_at, updated_at.
- `calendar_events`: UUID id, calendar_id, external_event_id, title, description, start_epoch_ms, end_epoch_ms, is_all_day, location, attendees_count, last_synced_at.
- `meeting_notes`: UUID id, event_snapshot_id (FK), title, content, created_at, updated_at.
- `video_items`: UUID id, url, title, tags_json, notes, priority, estimated_duration_minutes, watch_intent, is_archived, created_at, updated_at.
- `reel_counter_sessions`: UUID id, local_date, started_at_epoch_ms, paused_at_epoch_ms, stopped_at_epoch_ms, status, created_at, updated_at.
- `counter_events`: UUID id, session_id (FK), timestamp_epoch_ms, action, sequence_number (UNIQUE index on session_id + sequence_number).
- `daily_reel_statistics`: local_date (PK), manual_count, active_duration_ms, instagram_foreground_duration_ms, confidence.

## 4. Navigation Map
Bottom Navigation:
1. `today` -> TodayScreen (Tasks, Quick capture, Offline NLP parser)
2. `agenda` -> AgendaScreen (Read-only timeline, local meeting notes)
3. `video_inbox` -> VideoInboxScreen (Distraction-free queue, tags, external open)
4. `attention` -> AttentionScreen (Usage dashboard, Instagram counter)
5. `settings` -> SettingsScreen (Export data, Import data, Erase all data, Privacy controls)

## 5. Phase 0 Checklist
- [x] Multi-module Gradle configuration with Version Catalog
- [x] App shell with OrbitApplication, Hilt setup, and MainActivity
- [x] Bottom navigation bar with Material 3 design system and OrbitTheme
- [x] Room database (OrbitDatabase v1) with all schema tables, DAOs, TypeConverters, exportSchema enabled
- [x] Room migration test infrastructure and schema export
- [x] DataStore preferences repository (OverlayPreference, FeatureFlags, privacy options)
- [x] Android Keystore encryption manager (CryptoManager) and checksum verifier (ChecksumVerifier)
- [x] Privacy-first error handling (OrbitResult, OrbitError) that never leaks user content to logs
- [x] Export-my-data, Import-data with schema & integrity validation, and Delete-all-data use cases and UI
- [x] WorkManager initialization with Hilt integration
- [x] Comprehensive unit tests across security, data, domain, and UI components

## 6. Test Strategy
- Unit tests for CryptoManager and ChecksumVerifier
- In-memory Room tests for DAOs verifying constraints, cascade rules, and event ordering
- DataStore repository tests verifying preference mutations
- BackupRepository tests verifying JSON export, tampered import rejection, and clean restore
- Compose UI test for bottom navigation and shell flow
