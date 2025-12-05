# Publishing Kompact

This guide walks through everything needed to ship Kompact on both F-Droid and the Google Play Store, highlighting differences, prerequisites, and validation checklists.

## Shared prerequisites
- All code must be licensed under **GNU AGPL-3.0-or-later** (`LICENSE`).
- Update `versionCode`/`versionName` in `app/build.gradle.kts` and create a matching Git tag `vX.Y.Z`.
- Run `./gradlew clean assembleRelease` and `./gradlew bundleRelease` on the same machine to guarantee reproducible artifacts.
- Ensure the app does not depend on proprietary libraries or Google Play Services components.
- Prepare changelog entries, screenshots, and marketing assets that can be reused across stores.

## F-Droid flow
1. **Refresh metadata**
   - Edit `fdroid/metadata/com.kompact.yml` with the real URLs (`Repo`, `IssueTracker`, `Changelog`).
   - Set the latest `versionName`, `versionCode`, and corresponding tag/commit.
2. **Local verification**
   - Install `fdroidserver` and `fdroidcl`.
   - Run `fdroid lint --metadata-path fdroid/metadata com.kompact` to validate the file.
   - Run `fdroid build --server --metadata-path fdroid/metadata com.kompact` to make sure `gradlew assembleRelease` succeeds without signing.
3. **Submit to fdroiddata**
   - Fork `https://gitlab.com/fdroid/fdroiddata`.
   - Copy `fdroid/metadata/com.kompact.yml` into the fork (same path) and open a Merge Request.
   - Include in the MR: source tag link, SHA256 of `app-release-unsigned.apk`, and a green `fdroid build` log.
4. **Future updates**
   - Bump `versionCode` and update the `CurrentVersion*` fields in the metadata.
   - F-Droid rebuilds the APK automatically, so no signing step is necessary.

## Google Play flow
1. **Configure the upload key**
   - Generate a dedicated keystore (`keytool -genkeypair ...`).
   - Export the environment variables required by the Gradle script:
     - `KS_PATH`: absolute keystore path
     - `KS_PASS`: keystore password
     - `KS_ALIAS`: key alias
     - `KEY_PASS`: key password
2. **Build and sign**
   - Run `./gradlew clean assembleRelease bundleRelease`.
   - The APK signs automatically if all variables are present; otherwise run `./gradlew signingReport` to inspect the config.
   - Upload `app-release.aab` to Play Console (mandatory). Keep the signed APK for internal testing or direct sideloads.
3. **Play Console setup**
   - Enable Play App Signing (recommended) and back up the upload certificate.
   - Fill in the "App content" sections:
     - **Data safety**: state that the app does not collect or share data.
     - **Content rating**: productivity/utility, no sensitive content.
     - **Privacy policy**: link to the README or a static page explaining no personal data is collected.
   - Create an Internal testing track before a production rollout.
4. **Rollout**
   - Attach `What's new` text (max ~500 characters per locale) covering the main changes.
   - Confirm the published SHA256 signature matches the build output.

## Recommended QA before every release
- Run image compression flows (JPEG, PNG, WebP) on API levels 24, 29, and 35.
- Verify SAF permissions with both internal and SD card sources.
- Confirm the app works offline (airplane mode) and the Material 3 UI respects light/dark themes.
- Execute `./gradlew test connectedDebugAndroidTest` on at least one device/emulator.

Following this checklist shortens review times for both F-Droid and Google Play while keeping manually uploaded builds in sync with reproducible ones.
