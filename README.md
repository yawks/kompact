<p><img src="https://git.naxod.com/luca/kompact/raw/commit/def6ef3cf88d30306fd173079c53eb90c7f2aa37/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="200" alt="Kompact"></p>

# Kompact - Image Editor

Kompact is an open-source Android app written in Kotlin with Jetpack Compose that edits and compresses images (JPEG, PNG, WebP, BMP, GIF, TIFF, ICO, HEIF, HEIC, AVIF, JXL) quickly and transparently. Official builds are produced and signed by the maintainer to keep a single line of trust that stays compliant with F-Droid and Google Play policies.

## Key Features
- Multi-selection of images through the Storage Access Framework, with batch processing.
- Compression by quality percentage (default: original quality) or target file size.
- Conversion between JPEG, PNG, WebP, BMP, GIF, TIFF, ICO, HEIF, HEIC, AVIF, JXL and Telegram PNG (the original format is kept when no edit requires re-encoding). HEIF/HEIC/AVIF/JXL are encoded with the open-source libheif/libavif/libjxl codecs via [awxkee's Android wrappers](https://github.com/awxkee) (Apache 2.0).
- Crop, resize (pixels or percentage) and rotate images.
- Multiple text overlays with custom size, color and position.
- Draw with pen and highlighter tools.
- Color filters (grayscale, sepia, vintage, cool, warm, custom hue) and adjustable brightness, contrast, saturation, warmth, shadows and highlights.
- Transparent or custom background.
- Edit or strip EXIF metadata, including GPS position.
- Side-by-side preview of source/output with synchronized quality and target size controls and estimated output size.
- Custom output file name (defaults to `<name>-edit`) and destination folder, with the option to delete original files and preserve the source file date.
- Result cards summarize original vs compressed size and the status of each operation.
- Modern Material 3 UI with light/dark support.
- Automatic export to the Pictures/Kompact directory with the option to pick any other destination.

> **Note:** PDF documents, audio tracks, and video files are not supported yet. They remain on the roadmap and will arrive in future releases once the image workflow is fully battle-tested.

## Design
- FDroid-inspired palette with emerald primary (#1C8F62), denim secondary (#2F5978), and orange accent (#B55300) for a clean, opinionated look.
- AA contrast maintained between text/icons and both light/dark surfaces for accessible viewing.

## Requirements
- Android Studio Ladybug or newer.
- Android Gradle Plugin 8.5+, Gradle 8.7.
- JDK 21 (recommended) or JDK 17 configured through `JAVA_HOME`.
- Android SDK 35, minSdk 24.

## Quick start
```bash
# from the project root
./gradlew assembleDebug
./gradlew test
./gradlew assembleRelease
```
Artifacts must be signed with your own keys when testing private builds because production signing happens exclusively on the maintainer release workstation.

## Project structure
- `app/src/main/java/com/kompact/data` contains the local repository and compression logic.
- `app/src/main/java/com/kompact/ui` hosts the ViewModel and main composables.
- `app/src/main/java/com/kompact/model` exposes shared domain models.
- `app/src/main/java/com/kompact/util` keeps reusable helpers.

## Distribution policy
- The codebase contains no proprietary services, so it remains eligible for F-Droid and other FLOSS catalogs.
- Release builds stay unsigned inside the repository to let F-Droid reproduce them with its keys.
- Google Play specific assets (screenshots, policy text, signing artifacts) are curated by the maintainer, and production uploads happen from the same release workstation.
- For third-party forks, change the application ID and sign with independent keys to avoid name collisions.

`docs/PUBLISHING.md` tracks the release checklist shared with platform maintainers, focusing on the steps reviewers need for compliance checks.

## Release governance
- Tags are created only by the maintainer after running the full QA pass.
- Official packages are distributed exclusively through the maintainer-operated channels (Git tags, eventual F-Droid entry, Google Play listing).
- Community contributions are welcome, but merging or publishing decisions always stay under upstream control to preserve a single trust source.

## F-Droid publication
The official source repository for F-Droid inclusion is https://git.naxod.com/luca/Kompact. A mirror at https://gitlab.com/naxodlab/kompact is maintained solely to facilitate F-Droid's CI pipeline. All official releases, tags, and source of truth remain in the primary repository. F-Droid's auto-update mechanism tracks tags from the official repo.

## License
Kompact is distributed under the **GNU Affero General Public License v3.0 only**. Refer to `LICENSE` for details and always include a link to the source when redistributing binaries or forks.
