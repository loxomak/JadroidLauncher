# Jadroid — a Minecraft: Java Edition launcher for Android

A Minecraft: Java Edition launcher for Android, written in Kotlin with Jetpack Compose and an
orange Material 3 theme.

Jadroid talks **only to official / first-party repositories** for game files and login:

| Purpose | Endpoint |
| --- | --- |
| Version manifest, version json | `https://piston-meta.mojang.com` (`mc/game/version_manifest_v2.json`) |
| Libraries | `https://libraries.minecraft.net` |
| Assets | `https://resources.download.minecraft.net` |
| Microsoft login | `https://login.microsoftonline.com/consumers/oauth2/v2.0` |
| Xbox Live / XSTS | `https://user.auth.xboxlive.com`, `https://xsts.auth.xboxlive.com` |
| Minecraft services | `https://api.minecraftservices.com` |
| Mod loader metadata | `https://meta.fabricmc.net/v2` (Fabric) |
| Mod files | `https://api.modrinth.com/v2` (Modrinth) |

## Features

* **Microsoft account login** via the OAuth 2.0 **device code** flow (no WebView, no passwords
  handled by the app). Full chain: Microsoft -> Xbox Live -> XSTS -> Minecraft services -> profile.
  Tokens are refreshed automatically on launch.
* **Local (offline) accounts**, unlocked only after a Microsoft account is connected. They use the
  deterministic `OfflinePlayer:<name>` UUID, so they behave exactly like vanilla offline profiles.
* **Official version downloading**: version manifest, version json, client jar, libraries,
  per-ABI native libraries and the full asset index with SHA-1 verification of every file.
* **Modding**: create an instance on **Fabric** (loader list from Fabric meta), browse/search mods on
  **Modrinth** filtered by Minecraft version + loader, install with SHA-1 verification, plus
  enable/disable/delete and manual `.jar` import.
* **Launching**: builds the exact java command line (placeholder substitution over the version json
  `arguments`, or legacy `minecraftArguments`), exports `launch.sh`, streams the game log into the
  app and can stop a running game.

## On-device Java runtime (important)

Android ships no JVM, so the game cannot start until an Android-compatible **Java runtime** has been
imported once:

1. Settings -> *Java runtime* -> **Import runtime (.zip)** and pick an archive that contains
   `bin/java` (e.g. an OpenJDK/Zulu Android build, or the runtime used by PojavLauncher-style
   builds).
2. The archive is unpacked into `filesDir/runtime/<name>` and reused for every launch.
3. Advanced users can point Jadroid at a `java` executable elsewhere (Settings -> custom java
   executable) — Termux paths such as `/data/data/com.termux/files/usr/bin/java` also work.

Without a runtime the launcher still installs, verifies, mods and prepares everything; it reports a
clear error on launch instead of crashing.

## Project layout

```
app/src/main/java/com/jadroid/launcher/
├── core/          LauncherPaths, OkHttp client, SHA-1 hashing, JSON, logging
├── data/
│   ├── auth/      MicrosoftAuthService, XboxLiveService, MinecraftAuthService,
│   │              AuthRepository (+ local accounts), AccountStore
│   ├── mojang/    Piston meta models, MojangRepository, FabricRepository,
│   │              rule evaluation (OsProfile), VersionMerger/resolver
│   ├── minecraft/ MinecraftInstaller (plan + download + native extraction)
│   ├── download/  Parallel, resumable, SHA-1 verified DownloadManager
│   ├── instance/  Instance model + InstanceRepository
│   ├── mods/      ModrinthRepository, ModManager
│   └── settings/  SettingsStore (SharedPreferences + StateFlow)
├── launch/        LaunchCommandBuilder, JavaRuntimeManager, GameService
├── di/            AppContainer (hand rolled DI)
└── ui/            theme/, screens/, components/, viewmodels (Compose + StateFlow)
```

## On-device layout

```
filesDir/minecraft/
├── accounts.json                  accounts + active account
├── libraries/                     shared maven cache from libraries.minecraft.net
├── assets/{indexes,objects}/      official asset store (hashed objects)
└── instances/<instance>/
    ├── instance.json
    ├── launch.sh                  exported launch script
    └── .minecraft/                game directory
        ├── mods/                  Fabric mods (`.disabled` suffix = disabled)
        ├── saves/, logs/, options.txt, ...
        ├── natives/               extracted native libraries
        └── versions/<versionId>/<versionId>.jar
filesDir/runtime/<name>/           imported java runtimes
```

## Building

Requirements: JDK 17+ (JDK 21 recommended), Android SDK with platform 35 + build-tools 35.0.0.

```bash
# point Gradle to your SDK
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew assembleDebug          # debug APK in app/build/outputs/apk/debug/
./gradlew installDebug           # deploy to a connected device
```

Everything is `debug`-signable by default. The Microsoft **client id** used for the device-code flow
defaults to the well-known public Minecraft launcher id and can be overridden at build time or at
runtime (Settings -> *Microsoft application (client) id*):

```bash
./gradlew assembleDebug -Pjadroid.msClientId=<your-azure-app-id>
```

If you publish your own build, register your own Azure application (public client, "Allow public
client flow" enabled) so your users authenticate against your app id.

### Release builds & signing

The keystore is never stored in the repository. Provide it through environment variables (CI) or
Gradle properties (local), then build the release variant:

```bash
export JADROID_KEYSTORE_PATH=/absolute/path/jadroid-release.jks
export JADROID_KEYSTORE_PASSWORD=...
export JADROID_KEY_ALIAS=jadroid
export JADROID_KEY_PASSWORD=...

./gradlew assembleRelease        # signed APK in app/build/outputs/apk/release/
```

Create the keystore once with:

```bash
keytool -genkeypair -v -keystore jadroid-release.jks -alias jadroid \
  -keyalg RSA -keysize 2048 -validity 10000
```

`JADROID_*` variables can also live in `~/.gradle/gradle.properties` as `jadroid.keystorePath`,
`jadroid.keystorePassword`, `jadroid.keyAlias` and `jadroid.keyPassword`. When no keystore is
configured, `assembleRelease` falls back to the debug key and prints a warning, so release builds
stay installable for testing — never ship such an APK as an official release.

### Automated releases

`.github/workflows/android-release.yml` builds the app and publishes it:

1. add the repository secrets `JADROID_KEYSTORE_BASE64` (`base64 -w0 jadroid-release.jks`),
   `JADROID_KEYSTORE_PASSWORD`, `JADROID_KEY_ALIAS` and `JADROID_KEY_PASSWORD` (optional — without
   them the CI debug key is used),
2. push a tag:

```bash
git tag v1.0.1 && git push origin v1.0.1
```

The workflow builds `app-release.apk`, renames it to `jadroid-<tag>.apk`, writes a `.sha1` next to
it, keeps both as workflow artifacts and attaches them to the GitHub release for that tag. Running
the workflow manually (`workflow_dispatch`) only uploads the workflow artifacts.

## Notes & limitations

* Rule evaluation maps Android to the `linux` OS profile (that is what selects the `natives-linux`
  artifacts), and prefers `natives-linux-arm64` when a library publishes it for ARM64 devices.
* Login tokens and accounts are stored in app-private storage (`accounts.json`) and are only ever
  sent to Microsoft / Xbox Live / Minecraft endpoints.
* Local accounts cannot join online ("online mode") servers — that is a Minecraft limitation, not a
  Jadroid one.
* Mod files are installed per-instance; the shared library cache is never modified.
* Windows/macOS-only libraries are filtered out through Mojang's own rule sets, so no manual
  patching of version jsons is required.

