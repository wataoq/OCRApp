# Architecture

## Component boundaries

```
ui/library, ui/book, ui/editor    Compose screens + ViewModels
        │  (StateFlow<UiState>, immutable data classes, unidirectional data flow)
        ▼
data/repository/                  BookRepository, PageRepository, OcrRepository
        │
        ├──▶ data/local/          Room: BookDao, PageDao, OcrResultDao, AppDatabase
        ├──▶ storage/             ImageStorage interface, PageImageStorage impl
        └──▶ ocr/                 OcrEngine interface, MlKitOcrEngine impl
```

The UI layer never imports `androidx.room.*` or `com.google.mlkit.*`. Screens hold a
reference to `AppContainer` (via `LocalContext.current.bookOcrContainer()`) only to build
their ViewModel's factory; all actual data access happens inside repositories.

There is no separate domain/use-case layer. Repository methods (`createBook`,
`importPage`, `runInitialOcr`, ...) are the unit of business logic; a page's worth of
use-case classes wrapping single repository calls would add ceremony without simplifying
anything at this app's size.

## Data flow: importing and OCR'ing a page

1. `BookDetailScreen` launches the system Photo Picker (`ActivityResultContracts.PickVisualMedia`).
2. The returned content `Uri` goes to `BookDetailViewModel.importPage`, which calls
   `PageRepository.importPage`.
3. `PageRepository` delegates the byte copy to `ImageStorage.importImage`, which streams the
   Photo Picker `Uri` into a UUID-named file under `filesDir/page_images/` and returns the
   stored filename. The original `Uri` is not retained past this call.
4. `PageRepository` inserts a `PageEntity` referencing the stored filename. If the DB insert
   fails after the copy succeeded, the orphaned file is deleted.
5. Navigating into `EditorScreen` creates an `EditorViewModel` for that page. Its `init`
   block combines `PageRepository.observePage` and `OcrRepository.observeResult`; when there
   is a page but no OCR result yet, it automatically calls `OcrRepository.runInitialOcr`
   exactly once (guarded by `hasStartedOcrForThisLoad` / `isRunningOcr`).
6. `OcrRepository.runInitialOcr` calls `OcrEngine.recognize(imageFile)` (the ML Kit
   implementation downsamples the bitmap to at most 2000px on the long edge before handing
   it to the recognizer) and inserts an `OcrResultEntity` with `rawText == editedText`.
7. The Room `Flow` from `observeResult` re-emits, and the ViewModel updates its UI state to
   the `Ready` phase.

## Database entities

- **`books`**: `id`, `title`, `author?`, `createdAt`, `updatedAt`.
- **`pages`**: `id`, `bookId` (FK → `books.id`, `CASCADE`), `pageNumber?`,
  `storedImagePath`, `createdAt`, `updatedAt`. Indexed on `bookId`.
- **`ocr_results`**: `id`, `pageId` (FK → `pages.id`, `CASCADE`, unique index - one row per
  page), `rawText`, `editedText`, `engineId`, `ocrTimestamp`, `layoutJson?`.

Deleting a book cascades to its pages and their OCR results at the SQLite level. It does
**not** cascade to the filesystem - `BookRepository.deleteBook` reads the page rows before
deleting the book, then explicitly deletes each page's image file. The same pattern applies
to `PageRepository.deletePage`.

`layoutJson` is an optional serialized `List<OcrBlock>` (see below) - a JSON blob rather than
a normalized relational schema, so that persisting block/line/element layout doesn't require
its own tables for a feature the MVP UI doesn't yet render.

## OCR abstraction

```kotlin
interface OcrEngine {
    val engineId: String
    suspend fun recognize(imageFile: File): OcrDocument
}
```

`OcrDocument` (in `ocr/model/`) is engine-independent: `rawText`, `engineId`, and an optional
`blocks: List<OcrBlock>` tree (block → line → element, each with an optional bounding box).
`MlKitOcrEngine` is the only production implementation today; a future `CloudVisionOcrEngine`
or similar would implement the same interface and be swapped in via `AppContainer` without
touching `OcrRepository`, any ViewModel, or any screen. `FakeOcrEngine` (test-only) returns
deterministic fixture text so unit tests never depend on real ML Kit recognition quality.

## Image lifecycle

`ImageStorage` (interface) / `PageImageStorage` (impl) owns every page photo under
`filesDir/page_images/`. Three separate call sites read the same file for three different
purposes, each downsampled independently to the resolution that call site actually needs:

- `MlKitOcrEngine.recognize` - up to 2000px long edge, for recognition accuracy.
- `EditorScreen`'s source image - up to 1600px long edge, for on-screen zoom/pan.
- `PageThumbnail` (book detail list) - up to 200px, for a list row preview.

None of these load the full-resolution original into memory as a `Bitmap`; only the
OCR engine and the editor's zoom view need anything close to full detail, and both cap it.

## Key design decisions

- **No DI framework.** `AppContainer` is a ~10-line hand-rolled container; Hilt/Dagger would
  be pure overhead for one process, three repositories, and three ViewModels.
- **Dispatcher injection over hardcoded `Dispatchers.IO`/`Default`.** Every repository takes
  a `CoroutineDispatcher` constructor parameter (defaulted for production). This exists
  purely for test determinism: a ViewModel test that launches work via `viewModelScope.launch`
  and asserts after `advanceUntilIdle()` needs that work to run on the *same* test scheduler
  as `Dispatchers.Main`, or the assertion can race a real background thread.
- **`editedText` is ViewModel-owned, not Flow-derived, after first load.** See the
  rawText/editedText invariant in `CLAUDE.md`. This is the single most important invariant
  in the app and the reason `EditorViewModel`'s `init` block looks more careful than a
  typical "collect a Flow into UI state" ViewModel.
- **JSON blob for layout, not a relational schema.** `layoutJson` is deliberately not
  normalized into `blocks`/`lines`/`elements` tables. The MVP UI never queries into that
  structure (it only shows `rawText`/`editedText`), so a queryable schema would be pure
  speculative complexity today. Revisit if a future feature needs to query by bounding box.

## Not implemented (see README's future extension points)

CameraX capture, batch import, perspective/contrast correction, a second `OcrEngine`, OCR
engine comparison, AI-assisted correction suggestions, export, full-text search. The
architecture above was chosen so none of these require restructuring existing code.
