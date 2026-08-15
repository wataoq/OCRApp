# OCR limitations

BookOCR uses ML Kit Text Recognition v2's Japanese model, running entirely on-device. None
of the limitations below have been empirically tested against real book pages in this
environment (no device/emulator was available - see the build report in the PR/commit this
file shipped with). They are documented based on the known characteristics of ML Kit's
Japanese recognizer and this app's deliberately simple MVP scope, not measurement. Treat
them as "expect these failure modes," not "we verified these fail this way."

## Known / expected limitations

- **Vertical Japanese text (縦書き).** ML Kit's text recognizer is designed for horizontal
  text. Vertical layouts common in Japanese novels and many non-fiction books are likely to
  produce garbled reading order or missed characters. No vertical-specific preprocessing is
  implemented.
- **Ruby / furigana.** Small annotation glyphs above or beside kanji are likely to be
  misread, merged into the base text, or dropped entirely. There is no furigana-aware
  parsing.
- **Reading order.** `rawText` is exactly what ML Kit returns, in the order it returns it.
  For multi-column layouts, sidebars, footnotes, or captions, that order may not match how a
  human would read the page. The app does not attempt reading-order correction - this is
  precisely why the editor screen always shows the source image alongside the transcript,
  and why manual correction is a first-class, expected part of the workflow rather than a
  rare fallback.
- **Page curvature and perspective.** Photos of open books (especially near the spine) are
  often curved or shot at a slight angle. No curvature or perspective correction is applied
  before OCR; recognition quality will degrade toward the page edges/gutter.
- **Rotation.** Only a very slight rotation tolerance can be expected from the underlying
  model. Meaningfully rotated photos should be re-taken/re-imported rather than relied on.
- **Uncommon characters.** Rare kanji, old character forms (旧字体), gaiji, hand-drawn
  diagrams, and non-text marks are not guaranteed to be recognized correctly or at all.
- **Mixed-script and typography edge cases.** Headers, footers, running page numbers,
  footnote markers, unusual fonts, and italic/emphasis styling may be recognized as ordinary
  body text (merged into `rawText` without any structural distinction) or missed.
- **Photo quality dependency.** Recognition quality is dependent on lighting, focus, and
  resolution of the imported photo. The app downsamples to at most 2000px on the long edge
  before recognition (see `docs/ARCHITECTURE.md`), which is a memory/performance tradeoff,
  not a quality enhancement - very high resolution source photos will not improve recognition
  quality beyond that cap, but low-quality photos will still produce low-quality OCR.

## What the app does about this

Given the above, the product design leans on the human, not the model:

- The source image is always shown directly next to the editable transcript, never hidden
  behind a "trust the OCR" flow.
- `rawText` (unedited machine output) stays permanently accessible for comparison even after
  the user has corrected `editedText` - see the rawText/editedText invariant in `CLAUDE.md`.
- No automatic "cleanup" pass (spelling correction, punctuation normalization, reading-order
  reordering) is applied to OCR output. Any future automatic-correction feature must surface
  suggestions the user explicitly accepts, never silently rewrite `rawText` or `editedText`.

## Do not claim

Do not describe this app as supporting vertical text, furigana, or curved-page correction
unless and until each has actually been implemented and verified against real photographed
pages on a device.
