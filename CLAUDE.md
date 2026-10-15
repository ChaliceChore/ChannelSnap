# ChannelSnap — Claude Code Handoff

## What this app does

ChannelSnap is a **root-only Android utility** that backs up and restores blocked (disabled) notification channels across all installed apps. It's for power users who silence specific channels and don't want to redo that after a factory reset, ROM flash or device migration.

It's distributed on **GitHub as a sideloaded APK**, not on the Play Store, so Play policy (e.g. `QUERY_ALL_PACKAGES`, root usage) doesn't apply.

## Current state (Oct 2026)

Rewritten from scratch in Kotlin + Jetpack Compose (MVVM). The original Java app is kept, untracked, in `NotifBackup_source.zip`; a first partial Kotlin attempt is in `legacy_kotlin_attempt.zip`. Both are gitignored.

- **Scan**: algorithm unchanged from the Java app, which was confirmed working on a Pixel 8a, Android 16, KernelSU-Next. Parser now has unit tests.
- **Restore**: redesigned (see below) and **verified end to end on 2026-10-15** on a Pixel 8a, **Android 17 (API 37)**, KernelSU, debug build. A re-enabled Rapido channel came back as BLOCKED (`mImportance=0`, user-locked), and the other 26 reported ALREADY_BLOCKED.
- The dumpsys format on Android 17 is unchanged: 584 `AppSettings`, 932 channels, 26 blocked parsed correctly.
- Builds: `assembleDebug`, `assembleRelease` (R8) and `testDebugUnitTest` all pass.
- Not yet tested on a device: the **release** (R8-minified) APK, and Magisk.

## How it works

### Scan: `dumpsys notification --noredact` via root

`RootShell.run("dumpsys notification --noredact")` in one `su` session (~750 KB of output), parsed by `data/DumpsysParser.kt` (pure Kotlin, tested in `app/src/test`).

Dump format facts (Android 14–16):

```text
      AppSettings: com.rapido.passenger (10350) importance=DEFAULT userSet=true
        Delegate: com.google.android.gms (10161) enabled=true
        NotificationChannel{mId='promotional', mName=Discounts and News, mDescription=, mImportance=0, ..., mGroup='null', mAudioAttributes=AudioAttributes: ... bundle=null, ...}
```

- `AppSettings: <pkg> (<uid>)` is the only reliable package anchor; active records also carry `pkg=X ... uid=Y` on one line.
- `mId='…'` is single-quoted. `mName=` is unquoted. `mImportance=0` means blocked.
- Channel block regex is lazy `NotificationChannel\{.*?\}`, so it **stops at the first `}`** (inside AudioAttributes/VibrationEffect). That's fine because mId/mName/mImportance come before any nested braces.
- De-duplicate by `packageName|channelId`; sort by app label, then channel name.
- System packages with `importance=NONE` and no channels (e.g. `com.android.configupdater`) are expected.

Two fixes relative to the Java parser:
1. `mName` ends at the next `, mField=` instead of the next comma, so names like "Calls, messages" survive.
2. ChannelSnap's own `AppSettings` header is tracked and then filtered. The Java version skipped the header, which would have attributed the next channels to the previous app.

### Restore: root `app_process` helper (NOT `cmd notification`)

**`cmd notification set_importance` does not exist.** It's missing from AOSP's `NotificationShellCmd`; the only channel-level command is `set_bubbles_channel`. The original design could never have worked. The Java version also echoed `__DONE__pkg|id` unquoted, so the shell treated `|` as a pipe.

What works instead, verified against `NotificationManagerService` on AOSP main:
- `getNotificationChannelsForPackage(pkg, uid, includeDeleted)` → `enforceSystemOrSystemUI` → `isUidSystemOrPhone`, which **allows ROOT_UID**.
- `updateNotificationChannelForPackage(pkg, uid, channel)` → `checkCallerIsSystemOrSystemUiOrShell`, which **allows ROOT_UID and SHELL_UID**.

So `root/RootRestoreHelper.kt` writes a plan file to the app's cache, then runs one `su` session:

```text
export CLASSPATH='<base.apk>'
app_process /system/bin --nice-name=channelsnap_restore dev.chalicechore.channelsnap.root.RestoreMain '<plan>'
exit
```

`root/RestoreMain.kt` runs as uid 0. It reflects into `INotificationManager`, sets importance 0, **reads the channel back to verify**, and prints one `__DONE__<index>\t<STATUS>\t<detail>` sentinel per channel. `root/RestoreProtocol.kt` is the shared wire format (Base64 in the plan, so odd channel ids are safe) and is unit-tested. A channel with no sentinel counts as FAILED.

Restore outcomes: BLOCKED, ALREADY_BLOCKED, CHANNEL_MISSING (the app hasn't created the channel yet, so the user should open the app once), APP_MISSING, FAILED.

`RestoreMain` is kept by `proguard-rules.pro`. Nothing references it, so without the rule R8 would strip it.

### Why `getNotificationChannels()` from the app/shell fails

`INotificationManager.getNotificationChannels()` hits `checkCallerIsSameApp` (shell gets "Pkg com.android.shell cannot read channels for X"). That's why the scan uses dumpsys. Note: the `ForPackage` variants used by restore **do** accept uid 0. A future option is to move the scan into the root helper too, but dumpsys is proven, so leave it unless there's a reason.

## Backup file format (unchanged from v1)

```json
{
  "deviceModel": "Pixel 8a",
  "androidVersion": "16",
  "backupTimestamp": "2026-07-20T10:03:21Z",
  "totalDisabledChannels": 14,
  "channels": [
    { "packageName": "com.rapido.passenger", "appLabel": "Rapido", "channelId": "promotional", "channelName": "Discounts and News" }
  ]
}
```

## Tech stack

| Layer | Choice |
| --- | --- |
| Language | Kotlin 2.3 (AGP built-in Kotlin, so no `kotlin-android` plugin) |
| SDK | min 26, target/compile 36 |
| Build | AGP 9.2.1, Gradle 9.4.1, Kotlin DSL + `gradle/libs.versions.toml` |
| UI | Compose (BOM 2025.12.01), Material 3, dynamic colour |
| JSON | kotlinx.serialization |
| Root | `Runtime.exec("su")` in `data/RootShell.kt`; stdin approach works on Magisk and KernelSU. stderr is drained on a separate thread to avoid pipe deadlock. No libsu. |
| DI | none; `ChannelSnapApp` holds the repository and settings singletons |
| App icons | `PackageManager.getApplicationIcon` + LRU (`ui/components/AppIcon.kt`); no Coil needed |

Local builds: `JAVA_HOME` = Android Studio's `jbr`; `local.properties` holds `sdk.dir=C\:/Users/...` (the colon escaped, or lint errors). Google Drive sync sometimes locks files in `app/build`; delete it and rebuild if Gradle can't delete a directory. In Git Bash, set `MSYS_NO_PATHCONV=1` before `adb push/shell` with `/data/...` paths.

## Package layout

```text
dev.chalicechore.channelsnap/
├── ChannelSnapApp.kt
├── model/        ChannelBackupEntry, BackupFile, RestoreResult (+ RestoreOutcome)
├── data/         RootShell, DumpsysParser, NotificationBackupManager, SettingsStore
├── root/         RestoreMain (uid 0 entry), RestoreProtocol, RootRestoreHelper
├── repository/   BackupRepository (IO dispatch, SAF file I/O, shared debug log)
└── ui/
    ├── MainActivity.kt   (two tabs + full-screen restore flow; no nav library)
    ├── AppLinks.kt       (GitHub / issues / donate URLs; blank hides the row)
    ├── theme/
    ├── components/       AppIcon, ChannelGroupList, StateViews, LogConsole
    └── screens/          home/, restore/, settings/
```

## Sharp edges

1. **Restore is verified on KernelSU / Android 17 only.** If `app_process` fails under a particular root solution's SELinux domain, the helper's stderr shows in the debug log and every channel reports "Root helper failed to start".
2. **Don't stream-parse `su` stdout.** Read it after the process exits; the dumpsys output isn't reliably complete before that.
3. **Log console text isn't selectable** on purpose, because selection steals scroll gestures. Use the Copy button. The console is only composed when `BuildConfig.DEBUG`.
4. Backups use SAF only (CreateDocument, or a persisted OpenDocumentTree folder), so there's no storage permission and no FileProvider.
5. `AppLinks.DONATE` is blank (row hidden), and `SOURCE` assumes `github.com/chalicechore/ChannelSnap`. Update both when the repo is public.
