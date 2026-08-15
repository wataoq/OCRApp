# BookOCR

A private, offline Android app for digitizing passages from physical books: photograph a
page, run on-device Japanese OCR, and correct the result by hand while the source image
stays visible for comparison.

## MVP features

- Library of books (title, optional author, page count)
- Per-book page list, ordered by page number with a deterministic fallback for unnumbered pages
- Import a page photo via the system Photo Picker; the app keeps a private copy
- Capture a page with the system camera
- Non-destructive crop, perspective, contrast, and user-guided curved-page correction
- On-device Japanese OCR (ML Kit Text Recognition v2)
- EXIF-aware image display and OCR, with layout-aware horizontal/vertical block ordering
- Editor screen: zoomable source image above an editable transcript
- Strict separation of machine OCR output (`rawText`) from the user-corrected transcript
  (`editedText`) - see "The rawText/editedText invariant" below
- Manual correction, explicit save, reset-to-raw, and a safe re-run of OCR that never
  silently discards edits
- Everything stored locally: no accounts, no network calls, no analytics

## Architecture summary

Single Gradle module, layered UI → ViewModel → Repository → data sources:

```
ui/{library,book,editor}   Compose screens + ViewModels (StateFlow, immutable UI state)
data/repository/           BookRepository, PageRepository, OcrRepository
data/local/                Room entities, DAOs, AppDatabase
storage/                   ImageStorage interface + PageImageStorage (app-private files)
ocr/                       OcrEngine interface + MlKitOcrEngine + OcrDocument domain model
AppContainer.kt            Hand-rolled DI container (no Hilt/Dagger)
```

`OcrEngine` and `ImageStorage` are interfaces specifically so a future OCR backend (e.g. a
cloud engine) or storage strategy can be swapped in without touching the UI or persistence
code, and so both can be faked in tests without Android/ML Kit. See
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full data flow and design decisions,
and [`docs/OCR_LIMITATIONS.md`](docs/OCR_LIMITATIONS.md) for known OCR limitations.

### The rawText/editedText invariant

OCR output is source material and must never be silently rewritten. Every `ocr_results` row
keeps both fields:

- `rawText` - exactly what ML Kit returned, set once and never modified after the row exists
- `editedText` - the user's working transcript, initially equal to `rawText`

Editing only ever changes `editedText`. A manual OCR re-run merges safely: if the user never
touched `editedText` (it still equals the previous `rawText`), the new raw output replaces it
too; otherwise the edit is left untouched and only `rawText` is refreshed. See
`OcrRepository.rerunOcr` and the tests in `OcrRepositoryTest` / `EditorViewModelTest`.

## Build

```
./gradlew assembleDebug
./gradlew test
./gradlew lint
```

Requires an Android SDK (compileSdk 35) and network access to Google's Maven repository
(`dl.google.com`) for AndroidX/Compose/Room/ML Kit artifacts on first sync.

The project is verified with `assembleDebug`, unit tests, and Android Lint using JDK 21 and
Android SDK 35. Use Android Studio's bundled JDK or set `JAVA_HOME`; no machine-specific JDK path
is committed.

## Run

Open the project in Android Studio (Ladybug or newer) and run the `app` configuration on a
device or emulator with API 26+, or `./gradlew installDebug`.

## Privacy

- No accounts, no backend, no cloud sync, no analytics, no crash reporting, no ads
- All page images and OCR text stay in app-private storage (`allowBackup="false"`)
- No `INTERNET` permission is requested
- OCR runs entirely on-device via ML Kit's bundled Japanese model
- App logs never contain OCR text or page contents

## Known limitations

See [`docs/OCR_LIMITATIONS.md`](docs/OCR_LIMITATIONS.md) - vertical text, ruby/furigana,
reading order, page curvature/perspective, and more.

## Future extension points

The architecture deliberately leaves room for batch import, another local OCR engine behind the
same `OcrEngine` interface, OCR engine comparison, AI-assisted correction *suggestions* (never
silent rewrites), TXT/Markdown export, and full-text search. The proposed Tesseract 5 migration is
documented in [`docs/TESSERACT_MIGRATION.md`](docs/TESSERACT_MIGRATION.md).
