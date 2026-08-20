# Edgar4j Android Mobile Worker

Native Android worker for the Edgar4j distributed-download protocol. It is isolated from the Spring backend and React frontend under `mobile-worker-android/`.

## Behavior

- Opt-in only; disabled until configured and enabled in the app.
- Registers as worker platform `ANDROID` with `DOWNLOAD` and `SHA256` capabilities.
- Uses WorkManager with Wi-Fi/unmetered and charging constraints by default.
- Leases one task at a time and runs for at most eight minutes per WorkManager execution.
- Prevents periodic and manual worker executions from processing concurrently in the same app process.
- Advertises only the configured artifact capacity to the server, not all free device storage.
- Downloads SEC artifacts directly to app cache while computing SHA-256.
- Rejects non-HTTPS sources, redirects, and hosts outside `www.sec.gov`, `data.sec.gov`, and `efts.sec.gov`.
- Sends the configured SEC User-Agent identity on source requests.
- Heartbeats active leases every 60 seconds.
- Streams verified artifacts back to `/api/workers/tasks/{taskId}/artifact`.
- Abandons an active lease when WorkManager cancels the worker.
- Reports typed failures to the coordinator and relies on server lease expiry for process/device interruption recovery.
- Stores ordinary settings in DataStore and encrypts the HTTP Basic password using Android Keystore AES-GCM.
- Bounds worker API response bodies to protect the app from oversized responses.

## Server prerequisites

Enable distributed workers on Edgar4j and expose the server through HTTPS for release clients.

```bash
DISTRIBUTED_WORKERS_ENABLED=true
```

If Edgar4j API security is enabled, enter the configured HTTP Basic username/password in the app. The worker API is the existing `/api/workers` V1 protocol.

## App configuration

Open **Edgar4j Worker** and configure:

1. Server URL, for example `https://edgar.example.com`.
2. SEC User-Agent with your identity/contact address.
3. Optional Edgar4j HTTP Basic username/password.
4. Background-worker toggle.
5. Wi-Fi/unmetered and charging requirements.
6. Minimum battery percentage.
7. Maximum artifact size. Default is 10 MB; the app accepts 1-50 MB.

Press **Save** to apply the periodic WorkManager schedule or **Run now** to enqueue an immediate constrained run.

When HTTP Basic authentication is configured, the password is required when the username is first set or changed. Clearing the username removes the stored password.

Debug builds permit cleartext server URLs for LAN/emulator development. Release APKs require HTTPS.

## Local debug APK

Requirements:

- JDK 17
- Android SDK platform 36
- Android SDK Build Tools 36.0.0
- Gradle 9.1.0

```bash
cd mobile-worker-android
bash scripts/build-apk.sh debug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install with ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Signed release APK

Create a private signing key once and keep it outside the repository:

```bash
keytool -genkeypair -v \
  -keystore edgar4j-worker.jks \
  -alias edgar4j-worker \
  -keyalg RSA \
  -keysize 4096 \
  -validity 3650
```

Set signing variables and build:

```bash
export ANDROID_KEYSTORE_PATH="$PWD/edgar4j-worker.jks"
export ANDROID_KEYSTORE_PASSWORD='...'
export ANDROID_KEY_ALIAS='edgar4j-worker'
export ANDROID_KEY_PASSWORD='...'
export ANDROID_VERSION_CODE=1
export ANDROID_VERSION_NAME='0.1.0'

bash scripts/build-apk.sh release
```

Signed APK:

```text
app/build/outputs/apk/release/app-release.apk
```

The release build runs release Lint and verifies the generated APK signature with `apksigner` before reporting success.

Never commit the keystore or passwords.

## GitHub Actions APK generation

Workflow: `.github/workflows/mobile-worker-apk.yml`

- Pushes that change `mobile-worker-android/**` build/test a debug APK automatically.
- `workflow_dispatch` can build either `debug` or `release`.
- Every build runs Android unit tests and build-type-specific Lint before APK assembly.
- Release builds require signing secrets and verify the generated APK signature.
- APKs are uploaded as GitHub Actions artifacts.

For signed release builds configure repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Encode the keystore as one-line base64 before adding the secret:

```bash
base64 < edgar4j-worker.jks | tr -d '\n'
```

Then run **Actions -> Mobile Worker APK -> Run workflow -> release**.

## Development checks

```bash
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The worker source-download and configuration policies have unit coverage under `app/src/test/`.
