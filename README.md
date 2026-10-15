# ChannelSnap

Back up and restore the notification channels you've switched off, across all your apps.

If you have spent an afternoon silencing "Promotions", "Recommended for you" and "Tips" channels app by app, you know you don't want to do it again after a factory reset, a ROM flash or a move to a new phone. ChannelSnap takes a snapshot of every blocked channel and puts them back later in one go.

> **Root required.** ChannelSnap needs [Magisk](https://github.com/topjohnwu/Magisk) or [KernelSU](https://kernelsu.org/). Android doesn't let regular apps read or change other apps' notification channels.

## Features

- **Scan** every installed app for blocked (`IMPORTANCE_NONE`) notification channels.
- **Choose** what to back up with per-app and per-channel checkboxes, search, and batch select, deselect and invert actions.
- **Back up** to a JSON file in any folder you choose (Downloads, a synced folder, a USB drive), then share it.
- **Restore** with a preview first. Pick what to restore, and get a per-channel report that flags apps that aren't installed or haven't created their channels yet.
- Material 3 with dynamic colour, plus light and dark themes.
- One root prompt per scan or restore, with no background service and no network access.

## Install

1. Download the latest `ChannelSnap-x.y.z.apk` from [Releases](https://github.com/chalicechore/ChannelSnap/releases).
2. Open it on your phone and allow installs from that source when asked.
3. Launch ChannelSnap and grant root when Magisk or KernelSU prompts you.

Requires Android 8.0 (API 26) or newer.

## Use

**Back up:** tap **Scan**, review the list (everything starts selected), then tap **Back up**. Set a default folder under **Settings → Backup folder** to skip the "save as" dialog each time.

**Restore:** on the new setup, install your apps and **open each one once**. Apps create their notification channels the first time they run, so a channel that doesn't exist yet can't be blocked. Then tap **Restore**, pick the backup file, review the selection and tap **Block N channels**. If some channels show **Open app first**, open those apps and restore again. Restoring is idempotent.

## How it works

| Step | Mechanism |
| --- | --- |
| Scan | `su -c dumpsys notification --noredact`, then the channel registry (`AppSettings:` → `NotificationChannel{…}`) is parsed for `mImportance=0`. The dump is produced inside `system_server`, which sidesteps the binder same-app check that stops ordinary callers from listing another app's channels. |
| Restore | A small helper class inside the APK runs as **uid 0** via `app_process` in a single `su` session. It calls `INotificationManager.getNotificationChannelsForPackage` and `updateNotificationChannelForPackage`, the same path the Settings app uses, so the block is user-locked exactly as if you had flipped the switch yourself. Each change is read back to verify it. |

`cmd notification` can't do this, because it has no command that sets a channel's importance.

### Backup format

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

Only `packageName` and `channelId` are needed to restore. The other fields are there so the file is readable.

## Build

Requirements: JDK 17+ (Android Studio's bundled JBR works) and the Android SDK with platform 36.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # parser + restore-protocol tests
./gradlew assembleRelease      # minified; unsigned unless signing is configured
```

Debug builds install alongside release builds (`.debug` suffix) and show a developer log console at the bottom of the screen.

### Signing

The release build signs itself when it finds these values, either as environment variables or in an untracked `keystore.properties` at the repo root (without the `CHANNELSNAP_` prefix):

| Variable | Meaning |
| --- | --- |
| `CHANNELSNAP_STORE_FILE` | Path to the `.jks`, relative to the repo root |
| `CHANNELSNAP_STORE_PASSWORD` | Keystore password |
| `CHANNELSNAP_KEY_ALIAS` | Key alias |
| `CHANNELSNAP_KEY_PASSWORD` | Key password |

Create a keystore once and keep it safe. Android refuses updates signed with a different key.

```bash
keytool -genkeypair -v -keystore release.jks -alias channelsnap -keyalg RSA -keysize 4096 -validity 10000
```

Without signing values you get an unsigned APK, which you can sign by hand with `apksigner sign --ks release.jks app/build/outputs/apk/release/ChannelSnap-*.apk`.

### Releases (GitHub Actions)

Pushing a tag such as `v1.0.0` runs [`.github/workflows/release.yml`](.github/workflows/release.yml). It runs the tests, builds the release APK with `versionName` taken from the tag, and attaches it to a GitHub release. To get signed builds, add these repository secrets: `KEYSTORE_BASE64` (`base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

## License

[MIT](LICENSE)
