# AquaShelf

AquaShelf is a lightweight, fully offline water diary for Android, presented as a kitchen cabinet:
every saved entry places one filled glass, bottle or neutral vessel on the selected day's shelf.

It records the amounts you enter. It does **not** measure hydration, calculate requirements or give
health advice.

## Features

- **Today's Shelf** — date, recorded volume, container breakdown (`3 glasses · 1 bottle`) and the
  total number of entries shown separately; one shelf object per saved entry, chronological left to
  right then onto the next shelf; more objects per shelf on wider screens.
- **Presets** — starts with Small glass 150 mL, Glass 250 mL, Bottle 500 mL. Up to 8 presets
  (Glass or Bottle, 1–5,000 mL, unique 1–24 character names, case-insensitive). Add, edit, delete,
  Move Up / Move Down. If every preset is deleted, Add Custom stays available.
- **Quick logging** — one tap = one entry at the current local date/time, saved before the shelf,
  totals and confirmation update. Duplicate delivery of the same tap is ignored; deliberate repeat
  taps create separate entries.
- **Add Custom / manual entry** — amount, Glass / Bottle / Custom, date, time, Save / Cancel. Whole
  mL only, 1–5,000 (technical input limits), today or a past date, no future timestamps.
- **Entry details** — amount, container type, preset-name snapshot, date and time, Edit, Delete
  (with confirmation).
- **Undo Last** — labelled with the amount it removes; removes the most recently *logged* entry on
  the displayed day (creation order, not drinking time). Repeatable until the shelf is empty; disabled
  on an empty shelf.
- **Week** — today and the previous six dates as miniature shelves (weekday/date, recorded volume,
  container counts, up to six objects and `+N more`). Days without entries say "No records".
  Earlier/Later navigation by seven-day ranges. Past days support manual entry, editing and deletion.
- **Reminders (optional, off by default)** — start, end (same day, end after start), interval
  60/90/120/180 min. Notification: "AquaShelf" / "Would you like to record some water?"; tapping it
  opens Today.
- **Settings** — presets, reminders, reduced decorative animation, privacy information, Clear all
  local data.
- Accessible list view equivalent to the shelf, labels with amount/type/time on every object,
  48 dp+ touch targets, font scaling, portrait/landscape/tablet/resizable layouts, edge-to-edge,
  display cutouts, keyboard insets, predictive Back.

## Architecture

Single `:app` module, Kotlin + Jetpack Compose (customized Material 3), Navigation Compose,
ViewModel + StateFlow with lifecycle-aware collection, coroutines, Room (KSP), DataStore, manual DI
(`AppContainer`).

```
com.egrmeister.aquashelf
├── data/local        Room entities, DAOs, database, DataStore preferences
├── data/repository   Room-backed stores + repositories, transactional reset
├── domain/entries    WaterEntry, validation, totals, shelf ordering, EntryService, ActionGuard
├── domain/presets    Preset, validation, PresetService
├── domain/history    Week ranges and day summaries
├── domain/reminders  ReminderPlanner (eligibility/stale rules), NotificationAccess
├── domain/time       TimeSource (injectable clock + zone), TodayProvider (midnight refresh)
├── reminders         Notification channel/posting, AlarmManager scheduler, receivers
└── ui                shelf, entry, week, settings, components, theme
```

Domain logic is pure Kotlin behind `EntryStore` / `PresetStore` ports, so it is unit-tested with
in-memory fakes and a fake clock/time zone.

## Toolchain (pinned)

| Component | Version |
|---|---|
| JDK | 17 (Temurin in CI) |
| Gradle (wrapper, committed) | 8.14.3 |
| Android Gradle Plugin | 8.11.1 |
| Kotlin / Compose compiler plugin | 2.2.0 |
| KSP | 2.2.0-2.0.2 |
| Compose BOM | 2025.07.00 |
| Room | 2.7.2 · DataStore 1.1.7 · Navigation 2.9.3 · Lifecycle 2.9.2 |
| compileSdk / targetSdk / minSdk | **36 / 36 / 26** |
| Build tools (CI) | 36.0.0 |

Android 16 (API 36) behaviour: edge-to-edge is enforced and handled with `enableEdgeToEdge()` plus
inset-aware layouts; predictive Back is enabled (`enableOnBackInvokedCallback`) and all custom back
handling uses `BackHandler`; the app does not lock orientation or aspect ratio, so large-screen
resizability changes have no effect; no exact alarms, foreground services or JobScheduler quotas are
used.

## Build commands

```bash
./gradlew testDebugUnitTest          # focused unit tests
./gradlew lintRelease                # release lint
./gradlew assembleRelease bundleRelease                       # signed, non-minified
./gradlew -Paquashelf.minify=true assembleRelease bundleRelease  # signed, R8 + resource shrinking
```

Outputs:

- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`
- R8 mapping: `app/build/outputs/mapping/release/mapping.txt`

**Google Play: upload only the AAB.** The APK is for local installation and verification.

## Data model, snapshots, counts and Undo

- `WaterEntryEntity`: id, amountMl, containerType, presetNameSnapshot (nullable), timestamp
  (absolute instant), loggedLocalDate, loggedLocalTime, zoneId, createdAt, updatedAt. Indexed by
  (date, time), (date, createdAt) and createdAt.
- `ContainerPresetEntity`: id, name, containerType, amountMl, displayOrder.
- Each entry stores the preset **name, type and amount used at logging time**. Editing or deleting a
  preset never changes or deletes earlier entries. Editing an entry keeps its preset-name snapshot
  unless it is explicitly converted to Custom.
- Counts and volume are always derived from saved entries using each entry's own amount (volume is a
  `Long`). A bottle counts as one bottle — never converted into glasses. Historical counts are never
  multiplied by current preset sizes.
- Undo Last removes the latest entry by **creation order** on the displayed day; the button carries
  the expected entry ID, so a repeated tap can't remove two. Deletion reports whether *this* call
  removed the row, so simultaneous actions can't remove the same entry twice.
- Logging, Undo, edits, preset reordering and Clear All Data run in Room transactions.
- Room schemas are exported to `app/schemas`. Version 1 has no migrations; future versions must add an
  explicit `Migration` in `AppDatabase.MIGRATIONS`. No destructive migration is configured.

## Weekly history and date attribution

Entries are grouped by their **saved local date**, so travelling across time zones does not silently
move them. Explicit editing of date/time updates the attribution (moving an entry to another date
updates both shelves). Today refreshes at midnight, on foreground return and after time / time-zone
changes, without touching stored data. A time inside a daylight-saving gap is rejected with a message;
a repeated (fall-back) time consistently uses the earlier offset. No online time check is used.
Future dates are read-only.

## Offline operation, privacy, storage and backup

- No `INTERNET` or `ACCESS_NETWORK_STATE`; no networking libraries, accounts, backend, Firebase, ads,
  analytics, payments, cloud services or remote content.
- Permissions: `POST_NOTIFICATIONS` (optional reminders) and `RECEIVE_BOOT_COMPLETED` (reminder
  recovery) only. CI checks the packaged APK, the AAB manifest and the merged release manifest
  against this allowlist.
- Records live in app-private storage (Room + DataStore). `allowBackup="false"`, and both
  `fullBackupContent` and `dataExtractionRules` exclude everything from cloud backup and device
  transfer.
- Privacy information is bundled in Settings → Privacy information.

## Reminders: permissions and delivery limits

- Disabled by default. On Android 13+, `POST_NOTIFICATIONS` is requested **only after** reminders are
  turned on, and only once. If denied, logging works normally, the blocked state is explained and an
  optional link to system notification settings is offered — no repeated prompts. Channel
  availability is checked too.
- One **inexact one-shot** `AlarmManager.set()` alarm at the next boundary (start, start + interval,
  … before end) in the current time zone. No `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`, foreground
  services, push, wake-lock or battery-exemption requests.
- Before posting: reminders still enabled, notifications deliverable, the boundary still exists in
  the current settings, still the intended date and still inside its slot (otherwise it is stale and
  skipped — no backlog), and suppressed if water was logged since the previous scheduled boundary.
  One notification ID is reused.
- Rescheduled after delivery, settings changes, reboot (`BOOT_COMPLETED`), time / time-zone changes
  and app update (`MY_PACKAGE_REPLACED`). Explicit immutable PendingIntents; the alarm receiver is not
  exported.
- **Android may delay reminders** (Doze, battery optimizations). **Force-stopping the app prevents
  reminders until it is opened again.**

## Signing (PKCS12) and GitHub Secrets

The release build type explicitly uses `signingConfigs.getByName("release")` (PKCS12). Missing
credentials **fail** release tasks; there is no debug-signing fallback.

- CI: four repository secrets — `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
  `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` (Settings → Secrets and variables → Actions).
- Local: the keystore and signing data live **outside the project** in `../_keys/AquaShelf/`
  (`signing.properties` next to the `.p12`); override with `ANDROID_SIGNING_PROPERTIES` or the
  `ANDROID_*` environment variables. Keystores, passwords, Base64 and signing files are git-ignored
  and never printed by CI.

## CI

`.github/workflows/android.yml` uses the committed wrapper and the pinned toolchain, then: wrapper
validation → unit tests → Room schema check → `lintRelease` → requires all four secrets → builds the
signed non-minified APK + AAB → `apksigner verify --print-certs` (fails on errors or
`CN=Android Debug`) → `jarsigner` AAB integrity + expected signer (the certificate from the keystore;
self-signed upload certificates are accepted) → permission allowlist → native-library / 16 KB checks
(`zipalign -c -P 16`) → uploads verified artifacts and a release report → deletes the decoded
keystore. Setting `R8_PASS: "true"` repeats every check on an R8 + resource-shrinking build and keeps
`mapping.txt`. No emulator smoke test is required.

## 16 KB page-size findings

AquaShelf has no NDK code, and its dependencies (AndroidX, Compose, Room, DataStore, coroutines) ship
no native libraries for the app; CI lists every `.so` in the APK and AAB. When none are present, ELF
alignment does not apply and only APK zip alignment is verified (`zipalign -c -P 16`). If a dependency
ever adds `.so` files, CI checks their ELF `LOAD` alignment (≥ 2**14) and the report notes that runtime
testing in a 16 KB environment is still required. API 36 alone does not prove compatibility.

## Local installation and logcat verification

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat --pid=$(adb shell pidof -s com.egrmeister.aquashelf)
adb logcat -s AquaShelfReminders AndroidRuntime
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## Artifact size and pending checks

See `VERIFICATION.md` for the latest CI-reported sizes and the status of each manual check.
