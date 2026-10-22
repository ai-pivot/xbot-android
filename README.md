# xbot-android

A pure-native Android client for the xbot server, built with Kotlin + Jetpack Compose.

It talks to an **unmodified xbot server** over the existing web surface:

- `POST /api/*` — auth, sessions, history, send/cancel, AskUser, uploads
- `POST /api/rpc` — the existing 137-method RPC table (settings, active progress, pending AskUser, …)
- `GET /api/sse` — live stream (text, structured progress, tool state, AskUser, queue, session state)

No server-side RPC or protocol changes are required.

## Status

Early MVP. Implemented:

- Username/password login (cookie session, persisted with OkHttp `CookieJar`)
- Silent re-login after server restarts (credentials in Android Keystore AES-GCM)
- Session list from `/api/session-tree`, running badge, polling refresh
- Create / rename / delete sessions
- Chat history with turn/iteration grouping
- Live SSE streaming with `Last-Event-ID` resume, heartbeat watchdog, exponential backoff
- Streaming deltas + checkpoints (`stream_content` / `stream_delta`)
- Tool cards (status, elapsed, args, markdown output)
- Reasoning blocks, native Markdown rendering, code blocks
- AskUser dialog (options, multi-select, free text, cancel)
- Send, queue while busy, stop generation, attachments (multipart upload)
- FCM push notifications: AskUser / turn-complete while backgrounded, with deep link
- Light/dark Material 3 theme

## Known limitations

- **Push depends on Google Play services (FCM).** The server sends data-only messages;
  the app renders them locally. A user "force stop" still blocks all push (Android
  system behaviour), and some OEM battery managers delay data-only messages.
  The foreground SSE remains the live-stream path while the app is visible.
- **Plugin / GenUI views are not rendered.** The web plugin runtime is JavaScript/React.
  Tool output and normal Markdown are native; `ui_mode=genui` content is shown as text.
- No offline cache yet (history is fetched from the server).
- No settings/plugin-market/terminal parity with the web UI.
- API contract is internal and unversioned; pin the server version you test against.

## Requirements

- Android 8.0 (API 26) or newer
- An xbot server reachable from the phone (`web.enable=true`)
- A web account on that server (the first account can be created from this app on a fresh server)

## Build

The repository includes a Gradle wrapper. If `./gradlew` is not executable:

```bash
chmod +x gradlew
./gradlew :app:assembleDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

Prerequisites: JDK 17+ (tested with Temurin 21) and an Android SDK with
`platforms;android-35` / `build-tools;35.0.0`. Point Gradle at the SDK with
`local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

Unit tests (pure JVM state-machine tests):

```bash
./gradlew :app:testDebugUnitTest
```

## Server setup

1. Enable the web channel in `~/.xbot/config.json`:

```json
{
  "web": {
    "enable": true,
    "host": "0.0.0.0",
    "port": 16000
  }
}
```

2. Start xbot (`xbot-cli serve` or the `xbot-server` service).
3. In the app, enter `http://<server-lan-ip>:16000`, then log in or create the first account.

For public networks, put xbot behind HTTPS and set `web.public_url` to the HTTPS origin;
the session cookie is only marked `Secure` when `public_url` starts with `https://`.
The app currently allows cleartext HTTP for LAN use (`usesCleartextTraffic="true"`).

## Architecture

```
app/src/main/java/dev/xbot/android/
  data/        wire models, REST/RPC client, cookie jar, SSE client, prefs
  chat/        pure chat reducer + ChatViewModel
  sessions/    session list view model/screen
  auth/        login view model/screen
  push/        FCM registration, notification rendering, foreground presence
  ui/          Compose navigation, chat/settings screens, markdown/tool components
```

The core of the client is `chat/ChatReducer.kt` — intentionally a pure function of
`(state, event)`. It mirrors the important parts of the web client's state machine:
one turn per `turn_id`, iterations merged by number, stream checkpoints replace and
deltas append, and turn-ending signals never discard rendered content.

## Repository hygiene

Do **not** commit local secrets or machine-specific paths:

- `keystore.properties` and `keystore/` (release signing key + passwords)
- `local.properties` (Android SDK path)
- `dist/` (release APKs + `latest.json`)
- `tools/` (local JDK / Android SDK / Gradle downloads)

Create your signing config from the template:

```bash
cp keystore.properties.example keystore.properties
# then fill in storePassword / keyPassword
```

## Releases and update channels

Both channels publish the same signed APK plus a JSON manifest; the app only
compares `versionCode` and verifies `sha256` + the signer certificate, so it can
switch channels in place without losing data.

| Channel | Manifest URL | Produced by |
|---|---|---|
| stable | `https://github.com/ai-pivot/xbot-android/releases/latest/download/latest.json` | pushing a `v*` tag (`release.yml`) |
| dev | `https://github.com/ai-pivot/xbot-android/releases/download/dev/latest-dev.json` | every push to `master` (`dev.yml`, lands on the `dev` prerelease) |

Pick one in **Settings → 更新 → 更新清单地址** (nothing is hard-coded in the source),
then tap **检查更新** once to persist it; afterwards the app re-checks every 6 hours.

Releasing a stable version:

```bash
# 1. bump version.properties (versionCode must exceed the newest dev build —
#    release.yml fails with the exact number to use if it does not)
# 2. commit, merge to master
git tag v0.3.3 && git push origin v0.3.3
```

`dev.yml` numbers dev builds as `max(stable, previous dev) + 1`, so a later stable
release always outranks every dev build — switching a device from `dev` back to
`stable` is a normal upgrade, not a reinstall. A manifest *older* than the install
cannot be installed by Android; the update card says so instead of showing
"已是最新".

Update notes come from `CHANGELOG.md`: the workflow extracts only the
`## [x.y.z]` section matching the release.

## License

MIT — see [LICENSE](LICENSE).
