# EasyStock: What the App Must Add

> Note (app repo): the original spec says "Database v8". The app was already at v8 when this was written
> (v8 added `stock_review_line.info_fixed_at`), so every v8 below has been changed to v9, with a manual
> `Migration(8, 9)`. `01-server-mode-plan.md` and `03-server-spec.md` are not in this repository yet.
Everything the Kotlin Multiplatform app needs for server mode. The overall plan is in `01-server-mode-plan.md` and the server spec in `03-server-spec.md`.
Ground rules

* Standalone stays the default, and nothing in this document changes how the app behaves without a server.
* No server code runs and no network call is made until the user connects to a server.
* Shared code stays in `shared/src/commonMain`, with `expect`/`actual` only for platform APIs (Keystore, WorkManager, notifications, push).
* Every new user-facing string goes in `composeResources/values*/` in English, French and German.
* Every database change follows `.claude/rules/database.md` (DBML first, version +1, AutoMigration, schema JSON, `db-versions.md`, `MigrationTest`, backup compatibility).

README fix (Phase 0)

* Change the "Low stock" feature line so it describes only the badge and the "low stock" filter.
* Move "low-stock notifications" to the Roadmap under server mode.

New dependencies

* Ktor client core, the OkHttp engine for Android, content negotiation and the kotlinx.serialization JSON plugin.
* The Ktor `Auth` plugin with bearer tokens, which refreshes the access token automatically on a 401.
* AndroidX WorkManager for the background sync (Android only).
* The UnifiedPush Android connector library for the optional push.
* No Firebase and no Google Play Services dependency.

New packages

* `server/` holds the API client, the DTOs of the auth and admin endpoints, and the server connection state.
* `sync/` holds the sync engine: outbox reading, push, pull, applying rows, and the sync scheduler.
* `auth/` holds the session store (tokens), the role and permissions, and the connect, login and signup logic.
* `alerts/` holds the low-stock alert notifier.
* `composables/server/` holds the new screens (connect, account, admin).

Database v9
New tables (app only)

* `sync_state` (one row): server URL, certificate fingerprint, user ID, role, `since` cursor, last sync time, and an `applying` flag.
* `outbox`: table name, row ID, and the time of the change, one row per local change waiting to be pushed.
* `server_user`: user ID and display name, so the history can show who made each movement.
* `low_stock_alert`: alert ID, article ID, time of the crossing, stock at that time, and whether it was already notified.
* `rejected_change`: table, row ID, reason and time, for the "rejected changes" list.

Changed tables

* `stock_movement` gets `created_by` (uuid, nullable) and `device_id` (uuid, nullable).
* Both stay null in standalone mode and on Guest phones.
* `docs/schema.dbml` gets the two columns first, marked as "set by the server in server mode".

Outbox triggers

* Add `AFTER INSERT` and `AFTER UPDATE` triggers on every synced table: `category`, `attribute_definition`, `category_attribute`, `article`, `location`, `item`, `stock_movement`, `stock_review`, `stock_review_line`.
* Each trigger inserts the table name and row ID into `outbox`, or does nothing if that row is already waiting.
* The triggers fire only when `sync_state` exists and its `applying` flag is off, so standalone mode never fills the outbox.
* The sync engine sets `applying` while it writes pulled rows, so rows from the server are not sent back.
* The triggers are created in a manual `Migration(8, 9)`, since Room has no trigger annotation, and in the database open callback for fresh installs.
* `MigrationTest` checks that the triggers exist after the migration and that a write adds an outbox row only when connected.

Backups

* The backup format accepts v9 and still imports older backups.
* `created_by` and `device_id` are exported but ignored on import into a standalone phone.
* The new app-only tables (`sync_state`, `outbox`, `server_user`, alerts, rejected changes) are never part of a backup.

The QR code format

* All server QR codes use one prefix, so the existing scanner (`QrTransfer.parse`) can recognise them next to the backup QR codes.
* Format: `easystock://join?u=<server URL>&k=<kind>&t=<token>&fp=<certificate SHA-256>`.
* `kind` is `setup`, `invite`, `guest` or `reset`.
* `fp` is present only for HTTPS with a self-signed certificate.
* Extend `QrTransfer.parse` with a new `ScannedQr` case for join codes, and reuse the existing camera scanner screen.
* Mismatch (open): the server plan (`04-server-implementation-plan.md`) builds `easystock://<kind>?server=<URL>&token=<token>&fp=<hex>`
  instead. The app reads both formats (`JoinCode.parse`) and still builds `join`. One of the two documents must change.

HTTP and HTTPS

* Add a `network_security_config.xml` that allows plain HTTP, since self-hosters use any IP address or name.
* When the address uses HTTP, show a warning before connecting and an "unsecured" icon on the server screen.
* When the QR code carries a fingerprint, use a custom `X509TrustManager` that accepts only a certificate with that SHA-256.
* OkHttp's `CertificatePinner` is not enough for this, because a self-signed certificate fails the normal check before the pin is looked at.
* When there is no fingerprint, use the normal Android certificate check.

Session and tokens

* The access token stays in memory only.
* The refresh token is stored encrypted with an AES-GCM key from the Android Keystore, in a small DataStore file.
* Do not use `EncryptedSharedPreferences`, which is deprecated.
* Every request sends `X-EasyStock-App-Version` and `X-EasyStock-Schema-Version` headers.
* A 401 answered with "session revoked" triggers the wipe described under "Leaving a server".
* A 426 "app too old" shows a message asking the user to update the app, and pauses sync.

Connect screen

* New entry in Settings: "Server", which shows "Not connected" with a "Connect" button in standalone mode.
* The connect screen offers "Scan a QR code" and "Enter the address".
* Before anything else, the app calls `GET /server-info` to check that the server answers and that its API version is supported.
* After scanning a `guest` code, the app offers "Log in to my account" or "Continue as guest".
* After scanning an `invite` code, the app asks for a username, a display name and a password.
* After scanning a `setup` code, the app asks for the first Admin's username, display name and password.
* After scanning a `reset` code, the app asks for a new password.
* After typing an address, the app offers only "Log in" with username and password.
* The password field shows the rule (at least 10 characters) and nothing else.

The empty-phone rule

* Before joining, the app checks that every synced table is empty, soft-deleted rows included, and that the image folder is empty.
* If the phone is not empty, the app offers "Export a backup, then erase" or "Cancel".
* The erase deletes all rows and photos, which is allowed here because the data is leaving the phone, not being edited.
* Exception (to confirm): when the server says it is empty and the code is a `setup` code, the app offers to upload its data as the first content.
* That first upload sends every row through the normal push, in pages, then uploads every photo.

Sync engine

* One `SyncEngine` class in `commonMain` runs one cycle at a time, protected by a mutex.
* A cycle is: refresh the token if needed, push, pull, apply, then check for new alerts.
* Push reads up to 500 outbox rows, loads each row's current content, and sends them with a random batch ID.
* Rows are sent in dependency order: categories and attributes, then locations, articles, items, movements, reviews.
* For each row the server accepts, the app deletes it from the outbox.
* For each row the server changed (for example a renamed SKU), the app writes the server's version with `applying` on.
* For each row the server rejected, the app writes the server's version if there is one, and adds a `rejected_change` row.
* If the push fails on the network, the outbox stays as it is and the next cycle sends the same batch ID again.
* Pull asks for rows since `sync_state.since`, writes them with `applying` on, and saves the new `since` value in the same transaction.
* Pull continues page by page while the server says more rows are waiting.
* After applying rows, the app recomputes the `item.location_id` cache for the items whose movements changed.
* Movements received from the server are inserted, never updated, except during the full re-pull after a promotion.
* Viewer and Guest phones skip the push step, since their outbox is always empty.

When a sync runs

* When the app comes to the foreground.
* About 3 seconds after a local change, restarted on each new change, so quick taps make one sync.
* Every 15 minutes with a WorkManager periodic job that needs a network connection.
* When a UnifiedPush message arrives.
* When the user pulls down to refresh on the Stock page.

Photos

* Before pushing an article whose `image_path` changed, the app uploads the file to `PUT /images/{sha256}` if the server does not have it yet.
* The row sent to the server carries the image hash, not the local path.
* After pulling an article with an unknown image hash, the app downloads it to `images/<uuid>.jpg` in the background.
* A missing photo shows the normal placeholder until the download is done.

Roles in the app

* A `Permissions` object in `commonMain` lists what each role can do, matching the server's rules exactly.
* In standalone mode every permission is allowed, so nothing changes for standalone users.
* The UI hides buttons the role cannot use: stock actions, edit, delete, tag linking, reviews, settings screens.
* The server still checks everything, so the hidden buttons are for comfort, not for security.
* Guests do not see the movement history section on the article screen, nor the Reviews screen.
* Viewers see the history with the author's display name taken from `server_user`.
* The author line is shown only when `created_by` matches a `server_user` row. Standalone movements have no author, so no line.
* If a role changes while offline, the app updates the buttons at the next `GET /me`.
* The app reads the role from `sync_state.role` (`SessionDao.observeRole`). No row means standalone: everything allowed.
* The UI gets the rights from `LocalPermissions` (`auth/AppPermissions.kt`), the rules are in `auth/Permissions.kt`.
* The roles and their rights below are confirmed by the user:
  * Admin: everything, the admin screens included.
  * Editor: stock actions, edit, delete, tag linking, reviews, settings screens, history, alerts. No admin screens.
  * Viewer: reads the stock, the history and the reviews, can turn alerts on. Changes nothing, pushes nothing.
  * Guest: reads the stock only. No history, no reviews, no alerts.
  * A role unknown to the app version (newer server) gets the Guest rights.

What each role sees

| Element | Admin | Editor | Viewer | Guest |
|---|---|---|---|---|
| Stock list, article screen, location contents | yes | yes | yes | yes |
| New article button, edit icon, photo change | yes | yes | no | no |
| Stock actions (add, remove, move, unit actions) | yes | yes | no | no |
| NFC tag link and remove on the article, link or create from an unknown tag | yes | yes | no | no |
| Movement history | yes | yes | yes, with author | no |
| Info to fix cards on the article | yes | yes | yes | no |
| "Fixed" button on an info card (needs edit) | yes | yes | no | no |
| Reviews entry in Settings | yes | yes | yes, read-only | no |
| Start, count, finish, cancel a review | yes | yes | no | no |
| Settings: attributes, categories, locations, tags, backup and restore | yes | yes | no | no |
| Settings: reset data | yes | yes | no | no |
| Settings: language, app info | yes | yes | yes | yes |

* The tag of an article stays visible to Viewers and Guests when it has one, without the buttons.
* An unknown tag scanned by a Viewer or Guest shows a short message, no action.
* The app has no article delete button: delete exists only in the catalogue settings, which need the settings right.

Rejected changes screen

* New screen under Settings > Server: "Changes not accepted".
* Each entry shows what was changed, when, and the reason in plain words (no right, conflict, invalid value).
* A badge on the Settings tab shows the number of entries not yet seen.
* Entries can be dismissed one by one.

Account screen

* Shows the server address, the user's display name, the role, and the connection type (HTTP or HTTPS).
* For a Guest, shows the short code (e.g. `K7-4QX`) big enough for an Admin to read.
* For a Guest with a pending promotion, shows a banner "You have been given more rights: choose a username and password".
* That banner opens a form that calls `POST /auth/complete-promotion`.
* After a successful promotion, the app sets `since` to 0 and pulls everything again, with a progress indicator.
* Shows the "Log out" button and the last sync time.

Leaving a server

* "Log out" first checks the outbox, and if changes are waiting, offers "Sync then log out" or "Log out and lose them".
* Logging out calls `POST /auth/logout`, then deletes tokens, all rows, all photos, and the app-only tables.
* For a Guest, the confirmation says the account will be deleted.
* A "session revoked" answer from the server runs the same wipe, without calling `/auth/logout`.
* After the wipe, the app is an empty standalone app again.

Admin screens

* Shown only to Admins, under Settings > Server > Administration.
* Users: list with display name, role, kind (full or Guest), last sync, and a "pending promotion" label.
* User detail: change role, reset password (shows a `reset` QR code), disable, delete, see devices, revoke a device.
* Promote a Guest: shows the Guest's short code next to a field to type the code the person shows, and enables "Promote" only when they match.
* Invites: create an invite by choosing a role, show its QR code full screen, list open invites, cancel one.
* Printed QR code: turn it on or off, set the Guest limit, generate a new code, and export a PDF to print.
* Server backup: download a server backup file, and import a backup file into the server (merge or replace, with a strong confirmation for replace).
* Settings: retention of who made each movement, and which roles get alerts by default.

Printed QR code PDF

* Built on Android with `PdfDocument`, then shared with the normal share sheet so the user can print or save it.
* One A4 page with the QR code, the server name, and one line: "Scan with EasyStock to view the stock".
* The page does not contain the token in text form.

Low-stock alert notifications

* Alerts arrive with the normal pull and are stored in the local `low_stock_alert` table.
* After each sync, the app shows a notification for each alert not yet notified, then marks it notified.
* When more than 3 alerts arrive in one sync, the app shows one grouped notification with the list.
* Tapping a notification opens the article, or the Stock page filtered on "low stock" for a grouped one.
* One notification channel, "Low stock", which the user can turn off in Android settings.
* On Android 13 and later, the app asks for the notification permission right after joining a server, not at first launch.
* Guests never get alerts, and Viewers get them only if they turn them on in the account screen.
* Alerts are never created on the phone, only received from the server.

Push (optional)

* If a UnifiedPush distributor app is installed, the app offers to use it in the account screen.
* The app registers its UnifiedPush address with `POST /push/register`.
* A push message only starts a sync; the app ignores its content.
* Without a distributor, the 15-minute background sync is used, and the account screen says so.

Tests

* Unit tests (host): `Permissions` for every role and action, QR code parsing, sync request building, page handling.
* Device tests: v9 migration and triggers, outbox filled only when connected, `applying` flag blocks the trigger.
* Device tests: applying pulled rows, conflict answers, rejected rows, and the `item.location_id` recompute.
* Device tests: the wipe leaves no rows, no photos and no tokens.
* Device tests: the empty-phone check, including soft-deleted rows and photos.
* Shared JSON test cases for the merge rules, run against both `BackupMerge` (Kotlin) and the server (C#).
* A fake server in tests (Ktor `MockEngine`) for the sync engine, the refresh flow and the 401/426 handling.

Do this in a specific branch

Open points (connect and account screens)

* Request and answer field names of the auth calls (`AuthDtos.kt`, `ServerDtos.kt`) are provisional: the server only
  implements `/server-info` yet. They are the camelCase forms of the spec's names and must be checked once the
  endpoints exist.
* The role name: the server spec says `member` for the app's Editor. The app maps it both ways (`WireRole`).
* The server placeholder answers `supportedSchemaVersions: [1]`, the app sends schema 10 in
  `X-EasyStock-Schema-Version`. `/server-info` is not refused (the app checks only `apiVersion` and `minAppVersion`
  there), but every later call gets a 426 until the server lists 10. The app does not work around it: the sync
  pauses (see below).
* Not done yet: the setup exception (upload the phone's data as the first content of an empty server). The app
  always asks for an empty phone, a `setup` code included.
* Done: the Android 13+ notification permission after joining (see "Open points (low-stock alerts)").
* Logout without network: the app offers "Log out on this phone". The phone is wiped, but the session stays valid on
  the server until an Admin revokes the device or the refresh token expires.
* Logout with changes not sent: "Sync, then log out" runs a cycle, then logs out when the outbox is empty, or asks
  again with the number of changes left (sync failed, or rows changed meanwhile); "Log out and lose them" wipes the
  phone.
* "Export a backup, then erase" opens the typed erase confirmation, which links to the Backup screen. The export and
  the erase are two separate steps: the erase also deletes the temporary backup files, so it never runs right after
  an export without the user confirming it.

Open points (sync engine)

* Done: the engine (`sync/SyncEngine.kt`, store `sync/RoomSyncStore.kt` + `data/db/SyncDao.kt`), DB v10 tables
  (`pending_push`, `image_hash`, `rejected_change`), push, paged pull, photo upload and download, the role update
  from `GET /me`, triggers (foreground, 3 s after the last local change, pull to refresh on the Stock page, the full
  pull right after joining or a promotion), the sync row and the rejected changes screen on the Account screen, and
  the Settings badge for unseen rejected changes.
* Done: the 15-minute WorkManager job (`sync/BackgroundSync*.kt`, WorkManager 2.12.0): unique periodic work, network required, KEEP, exponential backoff. Scheduled while `sync_state` exists (join and app start), cancelled when it disappears (logout, leave, revoked session, reset); the worker also cancels itself when the cycle says NOT_CONNECTED. Runs `syncEngine(context).sync()`, so never two cycles at once. A network failure retries with backoff. Assumes nothing new of the server.
* Done: UnifiedPush (see "Open points (push)") and the low-stock alert notifications (see "Open points
  (low-stock alerts)").
* Done: `dataset-reset` (the server replaced its whole data set, server plan §1.2 #21). The engine
  (`sync/DatasetReset.kt`) keeps the server's `datasetEpoch` and sends it as `X-EasyStock-Dataset-Epoch` with push
  and pull. On a 409 `dataset-reset` (any call of the cycle), or a pull answer with another `datasetEpoch` than the
  one kept, it counts the unsent changes, erases every synced row, the outbox, the pending push, the rejected
  changes, alerts, users, photo hashes and photo files (`BackupDao.wipeServerData`, built from the wipe of a reset),
  sets `since` to 0 and pulls everything again in the same cycle. `sync_state` and the tokens stay: the user stays
  connected. A dialog says the server data was replaced, and how many unsent changes were lost. At most one reset
  per cycle: a second one fails the cycle (SERVER).
* Assumed about the server: the epoch is `datasetEpoch` in each pull answer; a request without the header is not
  checked (a pull from 0, after joining or a reset, sends none and takes the answer's epoch); the header is checked
  on `/sync/push` and `/sync/pull` at least. Without `datasetEpoch` in the answers, nothing is detected.
* The epoch is kept outside the database (`sync_dataset` preferences file) because this change adds no schema
  version. It is written before the first page of a full pull, read only when `since > 0`. To decide: a
  `sync_state.dataset_epoch` column (next DB version) would save it in the same transaction as `since`.
* Not done: offering a backup export of the unsent changes before the wipe (the plan says wipe and pull from 0; the
  changes may point at rows the new data set doesn't have). They are only counted in the dialog.
* The own `server_user` row (Account screen name) is kept by the reset wipe; the other users come back with the pull.
* The reset notice is kept in memory only: after a reset in a background cycle, if the process dies before the app
  is opened, the new data shows without the dialog. To decide: persist it.
* A phone connected before this feature has `since > 0` and no epoch: it takes the first epoch the server sends,
  so a replace done before the app update is not detected (server mode is not released yet).
* The push endpoint is not implemented on the server yet. The rejected reason codes are assumed: `not-allowed`,
  `invalid`, `missing-reference`, `review-open`, `conflict`; an unknown code is shown as is.
* A "changed" answer's `row` is read as partial (only the changed fields), and laid over the sent row. A full row
  works too.
* A new row the server rejects without a server version stays on this phone only (never synced, still in the
  rejected list). The user decides: delete it, or change it so it is sent again.
* Done: `MOVEMENT_KEPT` lets the user choose (`sync/MovementKept.kt`, `composables/server/MovementKeptRow.kt`). The
  server must send its kept movement back in `row` (status `changed`, table `stock_movement`). The app writes the
  server's movement over the local one with the same id, so the stock matches the server: the only case where the
  app changes a movement (the local one was never accepted, so it is not a synced row). The phone's version is kept
  in a `rejected_change` row, its wire row stored in `reason` as `movement-kept:<json>` (no schema change). The entry
  stays in "Changes not accepted" until the user picks:
  * "Use the server's version": the entry is removed, nothing else changes.
  * "Keep mine": the phone's quantity, article, location, reason and comment are added as a new movement (new id,
    `occurred_at` and `created_at` = now), pushed at the next sync. The server's movement stays (append-only).
    Offered only to roles with stock actions (Admin, Editor), and only when the rules of a stock action allow it
    now: not for a serial unit (the unit could be in two places) nor one half of a transfer, not when the article
    is deleted, an entry to a deleted location, or a removal larger than the stock now. To decide: offer it for
    units and transfers too.
  Without `row`, the phone keeps its own version, out of sync until the next full pull. The shared merge test case
  `docs/test-cases/merge/movement-is-never-changed.json` is unchanged (it tests the server merge, not this choice).
* A pulled row whose parent is still missing after the last page fails the cycle (error SERVER); the cursor does not
  move, the next cycle tries again. Pages are kept in memory until their parents arrive.
* The 426 pause lasts until the app restarts or joins another server. The Account screen shows "update the app".
* Done: a replaced photo is deleted after each successful sync cycle (`SyncStore.removeOrphanPhotos`): the `image_hash`
  rows no article row references (soft-deleted rows count as references), minus the hashes in the pending batch, lose
  their file and their row. Local replacements already delete the file in the view models (standalone too).
  Not done: a sweep of unknown files in `images/` (kept out on purpose: files staged by an open form look orphaned).
* A pulled article and location with the same NFC tag (both from the server) are not resolved: the server's unique
  rule must prevent it.
* Done: pull to refresh also works on the empty Stock page (the empty state scrolls). It only exists when connected.
* Backup import while connected: Replace is refused (in the UI and in `BackupDao.replaceAll`). Merge is allowed for
  Admin and Editor only: merged rows reach the outbox through the triggers and are pushed like local changes, so the
  server applies its own conflict rules to them. Viewer and Guest can't merge.
* Merge test cases shared with the server: `docs/test-cases/merge/*.json` (server plan §1.4 format, wire rows in
  camelCase), run against `planMerge` by `MergeTestCasesTest`. The Kotlin runner ignores `status`, `actorRole` and the
  `row` of each result; it checks the `conflict` codes and the `final` rows. `CATEGORY_RENAMED`, `SERIAL_RENAMED`,
  `BARCODE_CLEARED`, `LOCATION_PARENT_KEPT` and `MOVEMENT_KEPT` are app merge codes the server spec may not list yet.

Open points (low-stock alerts)

* Done: `alerts/AlertNotifier.kt` runs after each pull, inside the cycle (`SyncEngine` `afterPull`), so a background
  sync shows alerts too. The rules are in `planAlerts` (`alerts/AlertPlan.kt`, tested in `AlertPlanTest`); the
  Android notifications are in `alerts/AlertPlatform.android.kt` (iOS stub). The queries are in `data/db/AlertDao.kt`.
  No schema change: `low_stock_alert.notified` already existed.
* Each alert is shown at most once (`notified` set after the plan). Rows are never deleted: a later full pull would
  bring them back as new.
* The server sends no "resolved" signal, so the app decides with its own data: an alert is shown, and its
  notification kept, only while the article is active, not archived, quantity-tracked and at or below its threshold
  (local `SUM(quantity)`). After each sync, a shown notification whose articles are all above it is removed.
* The full pull after joining brings every past alert: only those of articles low right now, and at most 7 days old,
  are shown. The others are marked silently.
* An alert whose article is not on the phone yet (no foreign key) waits for a later sync, up to 7 days.
* One notification per article (tag = article id): a new alert for an article replaces its notification, with the
  current stock. More than 3 articles in one sync: one grouped notification that replaces the previous grouped one.
* Tap: the article on the Stock page, or the Stock page filtered on "low stock" for the grouped one.
* A `dataset-reset` (the server replaced its data set) removes every alert notification right after the wipe
  (`SyncEngine` `onDatasetReset`); the pull from 0 that follows then shows the current alerts like the full pull
  after joining.
* Leaving the server (logout, revoked session, reset) removes every alert notification. The "Low stock" channel is
  created as soon as the app runs connected, so it can be turned off before the first alert.
* Below Android 13, a sync started by the background job has no activity, so AppCompat's in-app language may not
  apply: such a notification (and the channel name) can use the system language.
* Guests and roles unknown to the app get none (`Action.RECEIVE_ALERTS`). Viewers: the app shows what the server
  sends; the server filters the Viewers who did not turn alerts on (server plan, decision 9). The account screen toggle
  (`PATCH /me` `alertsEnabled`) is not built yet.
* Permission (Android 13+): a short explanation, then the system dialog, once after each join, for roles that get
  alerts (not Guests). "Not now" or a denial changes nothing else: alerts are still marked as seen, so turning
  notifications on later does not show old ones. A Guest promoted later is not asked (Android settings only).

Open points (push)

* Done: UnifiedPush with the connector library `org.unifiedpush.android:connector` 3.3.5 (no Firebase, no Google Play
  Services; it brings Google Tink for the Web Push decryption, unused here). Code in `push/` (`PushRegistrar`,
  `KtorPushApi`), Android in `push/EasyStockPushService.kt` and `PushRegistrar.android.kt`, the row in
  `composables/server/PushRow.kt`.
* The Account screen lists the installed distributors; the user turns push on and picks one in a dialog (always a
  choice, even with one app). Without a distributor it says the 15-minute background sync is used.
* A push message calls `SyncEngine.requestSync()` and nothing else: its content is ignored.
* The opt-in and the address the server confirmed are kept in `noBackupFilesDir/unifiedpush.json`, not in the
  database (no schema change) and never in an Android backup. Every wipe (logout, revoked session, reset) unregisters
  from the distributor and deletes that file, without network.
* The app registers again with the distributor each time it comes to the front while connected (as the library
  advises), and sends the address to the server again each time (it upserts), so an address the server dropped (404
  or 410 once, restored server, new session after a promotion) comes back at the next foreground.
* Logout: `DELETE /push/register` first, best effort (10 s), then `POST /auth/logout`. Turning push off sends the
  same `DELETE`. A distributor that drops the app (uninstalled) also sends it.
* Assumed, to check with the server: the `POST /push/register` body is `{"endpoint": "<url>"}` (the spec doesn't give
  it), `DELETE /push/register` has no body, and both answer 2xx.
* No VAPID key and no Web Push keys are sent: the server POSTs the plain body `sync`. A distributor that answers
  `VAPID_REQUIRED` shows "refused", and the 15-minute sync is used.
* A sync started by a push runs in the engine's process scope (`requestSync()`): Android may stop the process before
  it ends. To decide: should a push enqueue an expedited one-time WorkManager job instead?
* To decide: the server sends a plain `sync` body. Some distributors may accept only encrypted Web Push; supporting
  them needs the app to send its `pubKeySet` (auth and p256dh) and the server to encrypt.
