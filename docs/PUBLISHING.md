# Publishing Kompact

This note captures the minimum information required to release Kompact while keeping ownership and signing under maintainer control. Everything below is what reviewers need to validate the process.

## Principles
- The project is licensed under **GNU AGPL-3.0-or-later**. Every artifact maps to a signed Git tag owned by the maintainer.
- Official binaries are produced on a single, reproducible workstation; unsigned outputs inside the repository exist only to let third parties verify F-Droid builds.
- No proprietary SDKs or Google Play Services components are linked, which keeps the app admissible on both F-Droid and Google Play.

## F-Droid overview
1. Update `fdroid/metadata/com.kompact.yml` with the new tag, versionCode, and public URLs.
2. Run the standard `fdroid build` pipeline locally to prove that `./gradlew assembleRelease` reproduces the unsigned APK.
3. Submit a merge request to `fdroiddata`, referencing the tag and checksum. The maintainer handles approvals to keep the supply chain centralized.
4. Whenever a new version ships, repeat the same metadata bump. F-Droid signs the resulting APK with its own keys; upstream does not distribute that binary directly.

## Google Play overview
1. Build the release bundle (`./gradlew bundleRelease`) on the maintainer workstation.
2. Sign the bundle with the maintainer upload key and deliver it through the Play Console.
3. Complete the required policy forms (Data Safety, Content Rating, Privacy) before rollout; the wording is maintained alongside the release assets so it stays consistent between stores.
4. Roll out via staged tracks; only the maintainer account has access to the production listing.

## QA snapshot
- Exercise JPEG/PNG/WebP compression scenarios on API 24, 29, and 35.
- Validate SAF permissions (internal storage + SD card) and offline behaviour.
- Run `./gradlew test` and `./gradlew connectedDebugAndroidTest` on at least one device/emulator before tagging.

Keeping the process intentionally lean protects the chain of custody while still providing enough documentation for external reviewers to audit the build path.
