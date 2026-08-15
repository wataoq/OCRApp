# CLAUDE.md — BookOCR

Durable rules for working on this repository. This is a private, single-user Android app
for digitizing physical book pages via on-device Japanese OCR. Read this before making
changes; it encodes decisions that should not be casually reversed.

## Product purpose

Accurately preserve text from physical books while always letting the user verify OCR
output against the source photo. Not a summarization tool, not a social app, not a cloud
service. See `README.md` for the MVP feature list and `docs/ARCHITECTURE.md` /
`docs/OCR_LIMITATIONS.md` for design detail and known limitations.

## Non-goals (do not add without an explicit product decision)

User accounts, authentication, a backend server, cloud sync (Drive/Firebase/etc.),
analytics, telemetry, ads, payments, sharing/social features, generative AI rewriting,
summarization, translation, embeddings/vector search, CameraX capture, Cloud Vision /
Document AI, batch scanning, PDF import, handwriting recognition, *automatic* page
detection / perspective / dewarp (the corrections here are user-guided, not automatic),
Play Store publishing, or multi-module Gradle architecture.

Note: OpenCV *is* used, but only for the user-guided curved-page dewarp, confined entirely
behind the `PageDewarper` interface (`imageprocessor/PageDewarper.kt`). This was an explicit
product decision (2026-08) reversing the earlier "no heavy OpenCV preprocessing" non-goal.
Do not spread OpenCV usage beyond `OpenCvPageDewarper`, and do not add *automatic* dewarp /
page detection on top of it without a new product decision.

## The rawText/editedText invariant (do not violate)

Every `ocr_results` row distinguishes:

- `rawText` - the untouched ML Kit output. Set once when a result row is created; never
  overwritten by user edits.
- `editedText` - the user's working transcript. Starts equal to `rawText`, then only ever
  changes through an explicit user action (typing + save, reset-to-raw) or the OCR rerun's
  safe-merge logic in `OcrRepository.rerunOcr`.

A re-run of OCR must never silently discard a user's corrections. The current rule: if
`editedText` still equals the previous `rawText` (user never touched it), the new raw output
replaces `editedText` too; otherwise `editedText` is left alone and only `rawText` is
refreshed. If you change this policy, update `OcrRepository.rerunOcr`,
`docs/ARCHITECTURE.md`, and the tests in `OcrRepositoryTest` / `EditorViewModelTest`
together - do not change one without the others.

`EditorViewModel` seeds `editedText` from persistence exactly once per page load (see the
`hasInitializedText` guard). Do not let a reactive Room `Flow` emission overwrite
`editedText` after that point - only explicit VM functions may change it. This is what
prevents a rotation, a background page-number edit, or a stray recomposition from clobbering
unsaved work.

## Architecture boundaries

- UI (`ui/`) never touches Room or ML Kit directly - only `data/repository/` and `ocr/`.
- OCR is accessed only through the `OcrEngine` interface (`ocr/OcrEngine.kt`). Do not
  reference `MlKitOcrEngine` or any ML Kit type outside `ocr/MlKitOcrEngine.kt`. A future
  cloud engine must be implementable as another `OcrEngine` without touching screens or
  persistence.
- Image storage is accessed only through the `ImageStorage` interface
  (`storage/PageImageStorage.kt`). Repositories depend on the interface, not
  `PageImageStorage` directly, so tests can substitute an in-memory fake.
- Image *correction* is non-destructive and lives behind two interfaces in `imageprocessor/`:
  `ImageProcessor` (crop/perspective/enhancement, Android-SDK only) and `PageDewarper`
  (curved-page dewarp, OpenCV). Neither leaks its implementation library to callers. The
  original photo is never overwritten; each correction is a *separate* generated file derived
  from `original + persisted settings/mesh`, so it can always be regenerated.
- A page holds up to three OCR-able variants - `ORIGINAL` / `PERSPECTIVE` / `DEWARPED`
  (`ProcessingVariant`) - plus `activeVariant`. `PageRepository.resolveImageFile` is the single
  OCR-integration point: it maps the active variant to a file (falling back to the original when
  that variant has no image on disk). Switching variants in the editor re-runs OCR through the
  same `OcrRepository.rerunOcr` path, so the rawText/editedText invariant still holds.
- No Hilt/Dagger. Dependencies flow through `AppContainer` / `DefaultAppContainer`
  (`AppContainer.kt`) and ViewModel factories built with `viewModelFactory { initializer {} }`.
  Keep it that way unless the app outgrows it.
- Deleting a book must delete its pages, their OCR results (Room FK cascade - see
  `AppDatabase`), and their image files (explicit cleanup in `BookRepository.deleteBook`,
  since Room cascade does not touch the filesystem). Keep DB and filesystem state
  consistent: clean up orphaned files on partial failure (see `PageRepository.importPage`).

## Dependency policy

- Prefer Android/Google first-party libraries; prefer stable releases over alpha/beta/RC.
- Don't add a dependency for something `kotlinx.coroutines`/standard Kotlin/Android APIs
  already do.
- Versions live in `gradle/libs.versions.toml`. When bumping, verify the AGP/Kotlin/KSP
  triple stays compatible (KSP's version string embeds the Kotlin version it targets).
- OpenCV (`org.opencv:opencv`) is the one heavy native dependency. It is packaged **arm64-v8a
  only** (`defaultConfig.ndk.abiFilters`) to keep the APK small; as a result the dewarp path
  does not run on x86/x86_64 emulators (`PageDewarper.isAvailable()` returns false and the UI
  disables dewarp). Keep it pinned to a stable 4.x release, not 5.x.

## Testability

- Repositories take `CoroutineDispatcher` constructor parameters (default
  `Dispatchers.IO`/`Default` in production). Tests pass a shared `TestDispatcher` (see
  `util/MainDispatcherRule`) so `advanceUntilIdle()` in ViewModel tests actually waits for
  repository-launched coroutines instead of racing a real background dispatcher.
- Fakes for `BookDao`/`PageDao`/`OcrResultDao`/`ImageStorage`/`OcrEngine` live in
  `src/test/java/com/local/bookocr/...` next to the tests that use them. Prefer them over
  Mockito-style mocking for these interfaces.
- `FakeOcrEngine` returns short, newly authored fixture text - never real book content.

## Build commands

```
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

Requires an Android SDK and network access to `dl.google.com` (Google's Maven repo) for
AndroidX/Compose/Room/ML Kit. If these commands cannot resolve dependencies or find the SDK,
that is an environment problem, not a code problem - do not "fix" it by weakening
`compileSdk`, removing dependencies, or disabling verification unless the user explicitly
asks for an offline/degraded build.

## Privacy constraints

No `INTERNET` permission unless a feature genuinely requires network access, and if one ever
does, get explicit product sign-off first - this app's privacy posture is "everything stays
on device" by default. No analytics/crash-reporting SaaS. Never log OCR text or page image
contents. `allowBackup="false"` is intentional; don't flip it without deciding what happens
to page photos in cloud backups.
