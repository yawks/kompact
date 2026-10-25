<p><img src="https://git.naxod.com/luca/kompact/raw/commit/def6ef3cf88d30306fd173079c53eb90c7f2aa37/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="200" alt="Kompact"></p>

# Kompact - Image Editor (yawks fork)

This is [yawks/kompact](https://github.com/yawks/kompact), a fork of
[Luca's Kompact](https://git.naxod.com/luca/kompact), retaining its history and
AGPL-3.0-only license. It adds **background, in-place folder shrinking with a
maximum megapixel limit**. The upstream application is credited below.

## New: shrink an existing photo folder

Open the navigation menu → **Shrink a photo folder** (French: **Réduire un dossier
de photos**), select a local folder, enter a maximum resolution (default **5 MP**),
optionally include subfolders, and start processing. Android 8+ is required for
this mode. Grant access to original location metadata when requested so Android
does not redact GPS while reading the originals. Notification permission makes
the foreground progress/stop control visible on Android 13+.

- Aspect ratio is retained. Photos at/below the limit are not re-encoded.
- JPEG is encoded at quality 85; PNG is lossless; WebP uses lossless encoding on
  Android 11+, quality 100 on Android 8–10. A result is used only if it is smaller.
- **JPEG, static non-palette 8-bit PNG and static WebP** are supported. Complete
  embedded EXIF/XMP/IPTC/ICC/comment/vendor metadata blocks are copied verbatim,
  not reconstructed from a selected list of EXIF fields. EXIF orientation is
  retained without rotating the stored pixel matrix. Original embedded
  thumbnails and dimension fields inside metadata remain verbatim; the new
  image header carries the actual resized dimensions.
- HEIC/HEIF, AVIF, JXL, RAW, GIF and other unsupported containers are **skipped**.
  Motion Photos, multi-picture/HDR auxiliary JPEGs, animated images,
  palette-dependent PNGs and wide-gamut images are also left unchanged. The
  existing editor's modern-format metadata injection is not a sufficient
  guarantee for destructive batch processing, so this mode does not use it.
- Files are updated **in place**, retaining their original filename, document
  URI, inode, filesystem birth time (where supported) and existing gallery
  identity. Last-modified time is restored and checked, including subsecond
  precision. This does not preserve POSIX `ctime` (inode-change time), which is
  not the creation date and necessarily changes on a write.
- Only local Android External Storage document trees are accepted, including
  SD cards where the provider supports this workflow. Before writing, the app
  checks that the open file is seekable and that its exact modification time
  can be restored. If the provider denies this, the photo is reported as an
  error and is not overwritten. Cloud storage is not supported by this mode.
- Processing runs through a unique foreground WorkManager job, with a persistent
  SQLite queue, progress notification, and stop action. Leaving the screen or
  turning it off does not cancel the work. Android can defer/restart work or
  impose foreground-service time limits; unfinished queued entries survive a
  process restart. A manually stopped job can be restarted from the screen;
  already reduced images are then below the threshold and skipped.
- A durable, checksummed copy of the **current** original is kept in private app
  storage before any overwrite. The result is decoded and its metadata checked
  before writing, then the written bytes and timestamp are verified. A failed
  write is rolled back. After process death, the next run restores any pending
  original before processing another image. Do not uninstall/clear application
  data while a recovery is pending: this private backup is needed for recovery.
  This is a recovery mechanism for an interrupted write, not a permanent undo
  archive of successfully resized photos.
- The compressed input is limited to 128 MiB and sampled decoded images to
  32 MP to bound memory use. Oversized/unreadable photos are reported individually.
  The UI shows totals, space saved and up to 100 skipped/error details; the
  complete per-file results remain in the app's local job database.

The fork uses application ID **`com.yawks.kompact`** and launcher name
**Kompact (yawks)**, allowing installation alongside the upstream app.

### Build and verification

The current upstream build uses JDK 17+, Gradle 9.8.1, AGP 9.4 and Android SDK
37 (the older requirements section below is upstream documentation).

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
# Connected Android 8+ device; tests only use generated files in app storage:
./gradlew :app:connectedDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Folder tests cover megapixel
budgets, raw metadata copying, unsupported/malformed containers, real image
decoding, exact timestamp/inode retention, concurrent-change rejection,
interrupted-write recovery and background-worker execution. Device tests do not
read or modify the user's photo collection. The upstream lint configuration
does not fail the build on existing lint findings.

---

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
