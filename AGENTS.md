# RomMP Agent Constitution

RomMP is a gamepad-first Compose music player that streams game soundtracks from
a user's self-hosted RomM server. This file is the structural law of the project
for ANY coding agent; CONTRIBUTING.md covers contributor expectations and the CI
jobs, and the release flow lives in the two skills named at the end. Rules here
carry their reason, because a rule whose reason you cannot state is a rule you
will break by accident.

## Build discipline

- ONE Gradle invocation at a time. Never two terminals, never `&`, never a
  second invocation while one is running. This project runs KSP for Hilt, Room,
  and Moshi, and concurrent invocations corrupt the KSP caches, which surfaces
  later as generated code that does not match the source. Pass multiple tasks to
  a single invocation and let Gradle schedule them.
- Do not run `./gradlew clean`. It forces a full cold rebuild for no diagnostic
  gain. When a build misbehaves, invalidate the specific stale output.
- `app/lint-baseline.xml` holds 77 findings captured when lint was adopted at
  0.3.1. They are UNTRIAGED. Nothing in that file has been reviewed and accepted;
  it is a snapshot of debt, not an exemption list. Never cite "it is in the
  baseline" as evidence a finding is benign, and never regenerate the baseline to
  silence a warning that just appeared, which is exactly the case the baseline
  exists to expose. Regenerating is a deliberate act after clearing real findings.
- Release builds are maintainer-only. `verifyReleaseSigningConfig` in
  `app/build.gradle.kts` fails `assembleRelease` and `bundleRelease` when
  `keystore.properties` is absent, because an APK signed with the debug key
  installs locally and can never be upgraded over an existing release. The guard
  is the point; do not route around it, and do not add the keystore to a build
  you were not asked to sign.

## Layout

One Gradle module, `:app`, package `com.rommapp.rommp`, sources under
`app/src/main/kotlin`.

- `data/auth` pairing and the stored credential.
- `data/remote` Retrofit interfaces, `dto/` for wire types, `RommApiFactory` for
  building clients, `Pagination.kt` for `fetchAllPages`.
- `data/music` repository interfaces and their Remote implementations, domain
  types (`Track`, `GameWithMusic`, `Playlist`), URL and DTO mapping.
- `data/playback` ExoPlayer, the MediaSession service, queue persistence.
- `data/download` Room database, entities, DAO, and the offline downloader.
- `data/network` connectivity.
- `di/` three modules: `NetworkModule` (OkHttp, Moshi), `DatabaseModule` (Room
  and every migration), `RepositoryModule` (`@Binds` interface to implementation).
- `ui/` one package per screen (`library`, `album`, `favorites`, `playlists`,
  `player`, `auth`), plus `input/` for FocusKit and `theme/` for the design
  tokens. `AppNav` holds the routes, `RootScreen` picks between the auth screen
  and the nav graph.

There is no `domain/` layer and none is wanted at this size. The repositories in
`data/` are the boundary. ViewModels depend on repository interfaces and on the
app-scoped singletons (`PlaybackManager`, `DownloadManager`, `NetworkMonitor`,
`TokenStore`), never on `DownloadDao` or `AppDatabase`. That currently holds with
zero violations; keep it that way.

## Patterns in use

- Hilt for everything. `RomMPApp` is `@HiltAndroidApp`, `MainActivity` and
  `PlaybackService` are `@AndroidEntryPoint`, ViewModels are `@HiltViewModel`,
  and shared state holders are `@Singleton`. Nothing is constructed by hand.
- Repository interface plus a Remote implementation, bound in
  `RepositoryModule`: `MusicRepository`/`RemoteMusicRepository`,
  `FavoritesRepository`/`RemoteFavoritesRepository`,
  `PlaylistsRepository`/`RemotePlaylistsRepository`,
  `CrateMembershipRepository`/`DefaultCrateMembershipRepository`. A new data
  source follows the same shape, and callers depend on the interface.
- Retrofit clients are built per server, not injected. The server address is
  unknown until the user pairs, so `RommApiFactory.create*(baseUrl, token)`
  builds a client on demand from credentials read out of `TokenStore` at call
  time. There is no injected `Retrofit` and there must not be one. The OkHttp
  client and Moshi instance ARE singletons and are shared by every client, the
  image loader, and the downloader.
- Compose with a ViewModel per screen exposing `StateFlow` of a single immutable
  ui state data class (`AlbumUiState`, and so on). State flows down, events go up
  as lambdas. Screens collect with `collectAsStateWithLifecycle`. ViewModels
  expose no mutable state.
- Room is offline state, not a cache in front of the network. `cached_tracks`
  holds every track the app has seen so an album renders offline with
  undownloaded rows greyed out; `downloaded_games` and `downloaded_tracks` record
  what is on disk. A schema change means all five of: the entity, a `Migration`
  in `DatabaseModule.kt`, adding it to `addMigrations`, the version bump on
  `@Database`, and the exported schema under `app/schemas`. There is no
  `fallbackToDestructiveMigration`, so a missing migration crashes on open, which
  is the intended outcome rather than silent data loss.
- Network, file, and database work runs off the main thread. `PlaybackManager` is
  the deliberate exception in the other direction: ExoPlayer is main-thread-only,
  so every player access is marshalled onto `Dispatchers.Main.immediate`.

## Security invariants

These are not preferences. A change that widens any of them is a regression no
matter what it enables.

- The bearer token travels only to the paired origin. `streamAuthHeaders` in
  `data/playback/PlaybackManager.kt` returns the `Authorization` header only when
  the URI scheme is `http` or `https` AND the scheme, host, and port all equal
  those of the stored base URL. Local `content://` and `file://` URIs never carry
  credentials. `RommApiFactory.normalizeOrigin` defines what the stored origin
  looks like (trimmed, no trailing slash, `https://` assumed for a bare host) and
  `TokenStore` holds the normalized form, so both sides compare the same string.
  Suffix matching, ignoring the port, and attaching the header before the check
  are each a security regression.
- `DownloadManager` attaches the header directly to `track.streamUrl`. That is
  safe only because `TrackMapping` builds every stream URL as the stored base URL
  plus a server-supplied path. Preserve that construction; a track URL that did
  not come from the paired server must not receive the header.
- The media session is exported. `PlaybackService` is `android:exported="true"`
  so the system's media surfaces can bind, which also lets any installed app
  connect as a controller. `ConnectCallback` fully trusts only same-uid, system
  uid, the media notification controller, and the Auto and Automotive
  controllers. Everything else connects with `COMMAND_SET_MEDIA_ITEM` and
  `COMMAND_CHANGE_MEDIA_ITEMS` removed, so an unrecognized controller keeps
  transport control but cannot hand the player a URI of its choosing. That gate
  is what stops an outside app from aiming the authenticated player at its own
  host. Do not grant unrecognized controllers item commands.
- `res/xml/network_security_config.xml` permits cleartext and trusts
  user-installed CAs because the server is self-hosted, plain HTTP on a LAN is
  normal, and its address is unknown at build time. There are no hardcoded hosts
  anywhere in the source. Adding one changes what the app talks to and needs
  `docs/PRIVACY_POLICY.md` updated in the same change.
- The auth DataStore is excluded from cloud backup and device transfer in both
  `res/xml/data_extraction_rules.xml` and `res/xml/backup_rules.xml`. Any new
  store holding a credential gets the same exclusion in both files.
- Anything that changes what leaves the device updates `docs/PRIVACY_POLICY.md`
  in the same change. That file makes checkable claims about permissions, network
  destinations, and local storage.

## Input and focus

The target hardware is retro handhelds. Every screen must be fully navigable with
a D-pad and face buttons. A control that only responds to touch is unfinished,
not a follow-up.

- `ui/input/FocusKit.kt` is the single focus vocabulary and its file KDoc is the
  specification. Read it before touching any screen's input. In short: A is the
  primary action, Y is the long-press equivalent, X is the row's inline
  affordance, B is close-topmost-else-back and belongs to the screen root alone,
  and L1/R1 are reserved for paging and transport.
- The cursor is Compose's native focus made visible. `CursorHost` is installed
  once, at `RootScreen`, and owns the idle-hide timer and the reveal
  interception. Focus position is never changed by hiding.
- A row is ONE focus stop. `cursorTarget` suppresses child focusables so inner
  buttons stay tappable but leave the D-pad order, and their actions ride the
  row's X and Y keys. Do not put a text field inside a `cursorTarget` subtree.
- Screen roots intercept ButtonB only. Never consume direction keys, A, X, or Y
  outside an open overlay; native focus traversal and the element grammar need
  them.
- Initial focus goes to primary content (the track list, the shelf), not the
  header, and the same request restores focus after a sheet closes.
- `MainActivity` translates `KEYCODE_F12`, which the target handheld emits when
  its lid rotates shut, into `PlayerLockController` requests. It is edge-triggered
  on the first key down, armed only while the player is on screen, and never
  consumes the event, so the system keyguard still runs.

## Structural index

- Pairing: `AuthRepository` runs init and poll, then `TokenStore` persists base
  URL, token, device id, and granted scopes in the `auth` DataStore.
  `RootViewModel` gates the nav graph on `isAuthenticated`, which is null while
  loading. The scopes requested at pairing are `AuthRepository.REQUESTED_SCOPES`;
  adding one requires the user to re-pair, so gate the feature with
  `TokenStore.hasScope` rather than assuming the token has it.
- The library listing crosses two endpoints on purpose:
  `RemoteMusicRepository.gamesWithMusic` pages the ROMs endpoint filtered to
  soundtracks and the music tracks endpoint filtered by duration, then
  intersects, because a ROM flagged as having a soundtrack can still have no
  track over the threshold and would open an empty album. The threshold is
  `DEFAULT_MIN_TRACK_SECONDS` in `MusicRepository.kt`, 30 seconds, which drops
  jingles and stingers. Change it in that one place.
- Cover and media URLs go through `encodedMediaUrl` in `data/music/MediaUrls.kt`.
  Server paths arrive unencoded and may carry a `?ts=` query, so each path
  segment is encoded separately. Encoding the whole string breaks the URL.
- Offline: `DownloadManager` writes audio into the shared MediaStore Music
  collection under `Music/RomM/<platform>/<game>` and requires Android 10 or
  newer, while cover art goes to `filesDir/covers` and metadata to Room. The file
  extension comes from the stream URL and is allowlisted to a short alphanumeric
  token because it lands in the MediaStore display name unescaped.
- Queue restore: `PlaybackStateStore` persists track ids, index, and position;
  `PlaybackQueueResolver` rebuilds `Track`s from the local tables with downloaded
  audio taking priority over the stream URL. Restore runs once, on the first
  state subscription, and yields to any queue the user starts first.
- Paging any list endpoint: `fetchAllPages` in `data/remote/Pagination.kt`.
- Images: `RomMPApp` is the `ImageLoaderFactory`. It shares the DI OkHttp client,
  sets `respectCacheHeaders(false)`, and keeps a 256 MB disk cache so covers
  survive going offline.
- User-facing text belongs in `res/values/strings.xml`, which today holds only
  the app name. The surrounding code is not the example to follow here; new work
  adds keys, and lint treats translation gaps as errors.

## Routing

- Cutting, tagging, or publishing a release, and version or APK naming rules
  -> `.claude/skills/release/SKILL.md`. Releases are maintainer-only.
- Running checks before a push or a release, validation tiers, signing proof
  -> `.claude/skills/pre-release-validation/SKILL.md`.
- Contributor expectations, code style, CI jobs, PR template -> CONTRIBUTING.md.
- What the app does with user data -> docs/PRIVACY_POLICY.md.
