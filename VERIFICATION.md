# AquaShelf — verification notes

Status legend: ✅ done · ⏳ pending · ⛔ blocked (reason given)

## Build and signing (CI)

| Check | Status | Notes |
|---|---|---|
| Focused unit tests (`testDebugUnitTest`) | ⏳ pending | First CI run |
| Release lint (`lintRelease`) | ⏳ pending | |
| Signed non-minified APK + AAB | ⏳ pending | |
| `apksigner verify --print-certs`, no `CN=Android Debug` | ⏳ pending | |
| AAB `jarsigner` integrity + expected signer | ⏳ pending | |
| Permission allowlist (APK, AAB, merged manifest) | ⏳ pending | |
| Native libraries / 16 KB | ⏳ pending | Expected: no `.so` files |
| R8 + resource shrinking pass, mapping preserved | ⏳ pending | Enabled only after the non-minified pass is green |
| Release artifact size | ⏳ pending | Filled from the CI release report |

Upload certificate SHA-256:
`5D:89:0F:7B:20:6D:DC:83:A0:79:97:A9:89:F2:7D:53:EA:BB:5A:95:84:4C:32:80:1B:9B:26:11:C8:2A:03:5C`

## Device checks (adb install of the signed release APK + logcat)

Device / Android version: ⏳ not recorded yet

| Check | Status |
|---|---|
| Offline launch | ⏳ pending |
| Preset logging and duplicate-tap protection | ⏳ pending |
| Manual (custom) entry, historical day requires time | ⏳ pending |
| Undo Last | ⏳ pending |
| Editing and moving an entry between dates | ⏳ pending |
| Week history, +N more, earlier ranges | ⏳ pending |
| Reminders, permission denial, settings link | ⏳ pending |
| Reboot recovery | ⏳ pending |
| Process recreation (draft kept) | ⏳ pending |
| Accessibility (TalkBack labels, list view, font scale) | ⏳ pending |
| Rotation (draft kept, layout) | ⏳ pending |
| Clear all data | ⏳ pending |
| 16 KB runtime environment | ⏳ only required if native libraries appear |
