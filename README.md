# Kompact

Kompact is an open-source Android app written in Kotlin with Jetpack Compose that compresses images (JPEG, PNG, WebP) and PDF documents quickly and transparently. The project is tailored for privacy-friendly distribution on F-Droid.

## Key Features
- Multi-selection of images or PDFs through the Storage Access Framework.
- Fully configurable compression parameters (quality, max size, output format, PDF DPI).
- Result cards summarize original vs compressed size and the status of each operation.
- Modern Material 3 UI with light/dark support.
- Automatic export to the Pictures/Kompact directory with the option to pick any other destination.
- Side-by-side preview of source/output with synchronized quality and target size controls.

## Design
- FDroid-inspired palette with emerald primary (#1C8F62), denim secondary (#2F5978), and orange accent (#B55300) for a clean, opinionated look.
- AA contrast maintained between text/icons and both light/dark surfaces for accessible viewing.

## Requirements
- Android Studio Ladybug or newer.
- Android Gradle Plugin 8.5+, Gradle 8.7.
- JDK 21 (recommended) or JDK 17 configured through `JAVA_HOME`.
- Android SDK 35, minSdk 24.

### Local SDK configuration
Create a `local.properties` file in the project root to point Gradle to your SDK installation:

```
sdk.dir=C:\Users\<you>\AppData\Local\Android\Sdk
```

Replace the path with the actual SDK location on your machine or set the `ANDROID_HOME` environment variable instead.

## Quick start
```bash
# from the project root
./gradlew tasks                    # optional sanity check
./gradlew assembleDebug            # build the debug APK
./gradlew connectedDebugAndroidTest  # run instrumented tests (device/emulator required)
```
Install the generated APK (`app/build/outputs/apk/debug/app-debug.apk`) on a device or emulator.

To prepare the F-Droid build run:

```bash
./gradlew assembleRelease   # produces app-release-unsigned.apk
apksigner sign --ks <keystore> --out app-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
apksigner verify app-release.apk
```
Signing is kept separate so that F-Droid can reproduce and sign the APK with its own key.

## Project structure
- `app/src/main/java/com/kompact/data` contains the local repository and compression logic.
- `app/src/main/java/com/kompact/ui` hosts the ViewModel and main composables.
- `app/src/main/java/com/kompact/model` exposes shared domain models.
- `app/src/main/java/com/kompact/util` keeps reusable helpers.

## F-Droid distribution
See `docs/PUBLISHING.md` for the full submission checklist targeting `fdroiddata`.
- No proprietary or non-free dependencies (only AndroidX, Compose, Material Components, Kotlin).
- `android.enableJetifier=false` plus the absence of closed-source binaries keeps builds reproducible.
- Google Play Services and tracking/analytics SDKs are not used.
- The release build stays unsigned so F-Droid can apply its own signature.
- Minification is disabled for the `release` build type to lighten R8 and simplify APK diffs.
- The project builds with `fdroid build` using the standard Gradle wrapper—no extra scripts required.
- `android.suppressUnsupportedCompileSdk=35` is already set in `gradle.properties` to keep logs tidy.
- A ready-to-copy metadata template lives at `fdroid/metadata/com.kompact.yml`.

### F-Droid metadata requirements
- Run `gradlew assembleRelease` without custom scripts.
- Pull artifacts from `app/build/outputs/apk/release/`.
- Declare the `AGPL-3.0-or-later` license and source `https://github.com/<your-user>/Kompact` (update once public).
- Update `Repo`, `IssueTracker`, and `Changelog` inside `fdroid/metadata/com.kompact.yml` with real URLs before filing the `fdroiddata` merge request.

## Google Play distribution
Additional steps and checklists are spelled out in `docs/PUBLISHING.md`.
- Produce the Android App Bundle with `./gradlew bundleRelease` (Play requirement) or via Android Studio's *Generate Signed Bundle/APK*.
- Enable Play App Signing by uploading the AAB signed with your upload key; Google manages the distribution key.
- Use a dedicated upload keystore and define `KS_PATH`, `KS_ALIAS`, `KS_PASS`, `KEY_PASS` environment variables before signing (never store them in git).
- Fill the Store Listing with the same assets shared with F-Droid (short/long descriptions, 1080x1920 screenshots, 1024x500 feature graphic, adaptive icons).
- Recommended attachments: `What's new` changelog, static privacy policy (no personal data collected), repository link.
- Before production rollout, create at least one Internal test track to validate signing and SAF permissions.

### Publication checklist
- Align `versionCode`/`versionName` in `app/build.gradle.kts` for every release and create a matching `vX.Y.Z` tag.
- Run `./gradlew clean assembleRelease` and `./gradlew bundleRelease` on the same machine used for signing.
- Sign `app-release-unsigned.apk` only for Play (F-Droid requires the unsigned artifact) and keep the SHA256 fingerprints.
- Update `fdroid/metadata/com.kompact.yml` and `fastlane/metadata` (if in use) with the latest changelog entries.
- Publish source code and graphics under the same tag to guarantee reproducibility.

## License
Kompact is distributed under the **GNU Affero General Public License v3.0 or later**. Refer to `LICENSE` for details and always include a link to the source when redistributing binaries or forks.
