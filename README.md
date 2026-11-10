# EasyStock

Free, open-source Android app to manage a physical stock with NFC tags. Scan the tag on a shelf or bin to see the article, update the quantity on the spot, and get notified when stock drops below a threshold. Built for small businesses (1-5 people), associations and hobbyists who can't justify paid tools. Works offline on the phone: no server, no account.

School project (TIP, CPNE-TI, 2026-2027). Target hardware budget: 100 CHF.

## Features

Planned scope, in priority order:

1. Scan an NFC tag to open the article and see its stock
2. Browse the article list, edit quantities
3. Low-stock notification (per-article threshold)
4. Stock review: scan tags one by one, compare counts, show the gaps
5. Locations (room > shelf > bin)
6. Backup file to transfer or restore the stock, QR code for small stocks
7. Optional, if time allows: server mode (multiple users, history)

3D-printable NFC tag holders will be published separately.

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

## License

[MIT](LICENSE) © 2026 EasyStock contributors. Free to use, change and share, as long as the copyright notice is kept.
