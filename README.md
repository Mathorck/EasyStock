<p align="center">
  <img src="docs/images/icon.png" alt="EasyStock icon" width="96">
</p>

<h1 align="center">EasyStock</h1>

<p align="center">Manage a physical stock with NFC tags. Free, open source, offline, no account.</p>

<p align="center">
  <a href="https://github.com/Mathorck/EasyStock/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/Mathorck/EasyStock"></a>
  <a href="https://github.com/Mathorck/EasyStock/actions/workflows/release.yml"><img alt="Release workflow" src="https://img.shields.io/github/actions/workflow/status/Mathorck/EasyStock/release.yml?label=release"></a>
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-blue"></a>
  <img alt="Platform: Android" src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="Min API 26" src="https://img.shields.io/badge/min%20API-26-brightgreen">
  <img alt="Kotlin 2.4.21" src="https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Compose Multiplatform 1.12.2" src="https://img.shields.io/badge/Compose%20Multiplatform-1.12.2-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="Offline first, no account" src="https://img.shields.io/badge/offline%20first-no%20account-485C43">
  <a href="https://github.com/Mathorck/EasyStock/releases/latest"><img alt="Download APK" src="https://img.shields.io/badge/Download-APK-485C43?logo=android&logoColor=white"></a>
</p>

EasyStock is an Android app to manage a physical stock with NFC tags. Scan the tag on a shelf or bin to see the article, update the quantity on the spot, and get notified when stock drops below a threshold. It is built for small businesses (1-5 people), associations and hobbyists who can't justify paid tools. Everything works offline on the phone: no server, no account, data stays on the device.

School project (TIP, CPNE-TI, 2026-2027). Target hardware budget: 100 CHF.

## Features

- **NFC scan:** scan a tag to open its article. Scanning a location tag: _to be confirmed_.
- **Stock list:** quantities per article, filters, search across names and attributes, add or remove stock in one tap.
- **Article photos:** take a picture or pick one from the gallery, then crop it.
- **Categories and attributes:** custom attributes per category (text, number, measurement with a unit chosen per value).
- **Name templates:** build the article name from its attributes.
- **Serial units and consumables:** track tools one by one (each unit has its own properties and location) or count consumables as a quantity.
- **Locations:** hierarchy (room > shelf > bin) with an icon and a color per location.
- **Low-stock threshold:** per article, with a notification (not for serial-tracked articles).
- **Stock review:** go through a location, tick off what is there and see the gaps in a summary.
- **Backup and transfer:** export to a file, a QR code or over the local Wi-Fi; optional password; merge into or replace the existing data.
- **Languages:** English, French, German.

3D-printable NFC tag holders will be published separately.

## Screenshots

<!-- Filled in with the screenshots in docs/images/screenshots -->

## Roadmap

- Server mode (multiple users, movement history). Optional, standalone stays the default.

## Prerequisites

- Android Studio (recent stable) with Android SDK 37
- JDK 21 (Gradle daemon toolchain, auto-provisioned)
- Android device or emulator, API 26+ (NFC needs a real device)
- iOS: Mac + Xcode. The iOS target compiles from the same code but is **not tested or supported**.

## Setup

1. Clone the repo
2. Open the root folder in Android Studio and let Gradle sync
3. Run the `androidApp` configuration, or:

```
./gradlew :androidApp:assembleDebug
```

## Tests

```
./gradlew :shared:testAndroidHostTest     # shared logic, JVM
./gradlew :shared:iosSimulatorArm64Test   # macOS only
```

## Environment variables

None.

## Architecture

Kotlin Multiplatform + Compose Multiplatform.

| Path | Content |
|---|---|
| `shared/src/commonMain` | All UI and logic shared by Android and iOS |
| `shared/src/commonMain/composeResources` | Icons and strings (`values`, `values-fr`, `values-de`) |
| `androidApp` | Android entry point (`MainActivity`) |
| `iosApp` | iOS entry point (Xcode project) |
| `docs/schema.dbml` | Database schema |

### Data model

Full schema: [`docs/schema.dbml`](docs/schema.dbml) (paste it into [dbdiagram.io](https://dbdiagram.io) to see the diagram). Same logical model on SQLite (phone) and PostgreSQL (future server).

- **Catalogue:** `category`, `attribute_definition`, `category_attribute`, `article`
- **Physical stock:** `location` (hierarchical), `item` (serial-tracked units)
- **Movements:** `stock_movement`, append-only. Stock = `SUM(quantity)`.

Rules: UUID keys generated on the device, UTC timestamps set by the app, soft delete via `deleted_at`.

## Contributing

Branch from `main`, open a pull request.

Commits follow [Conventional Commits](https://www.conventionalcommits.org):

```
<type>(<optional scope>): <imperative short description>
```

| Type | Use for |
|---|---|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `refactor` | Code change with no behavior change |
| `test` | Adding or fixing tests |
| `chore` | Build, dependencies, config |
| `style` | Formatting only |

Examples: `feat(scan): read NFC tag id`, `fix: keep selected tab on rotation`.

Code, comments and commit messages in English. Files in UTF-8.

## Releases

Tags are plain [SemVer](https://semver.org), no `v` prefix. Pushing a tag `X.Y.Z` runs [`.github/workflows/release.yml`](.github/workflows/release.yml): tests, signed release APK, then a GitHub release with the APK, its SHA-256 and notes generated by [git-cliff](https://git-cliff.org) from the commits ([`cliff.toml`](cliff.toml)). A tag with a suffix (`1.0.0-beta.1`) is published as a pre-release.

```
git tag 0.1.0
git push origin 0.1.0
```

`versionName` = tag, `versionCode` = `MAJOR*10000 + MINOR*100 + PATCH`.

Required repository secrets (Settings > Secrets and variables > Actions):

| Secret | Content |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Release keystore, base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

Never commit the keystore. Losing it means users can't update the app anymore, keep a backup outside the repo.

## License

[MIT](LICENSE) © 2026 EasyStock contributors. Free to use, change and share, as long as the copyright notice is kept.
