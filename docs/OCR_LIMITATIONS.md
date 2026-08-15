# OCR limitations

BookOCR uses ML Kit Text Recognition v2's Japanese model, running entirely on-device. None
of the limitations below have been empirically tested against real book pages in this
environment (no device/emulator was available - see the build report in the PR/commit this
file shipped with). They are documented based on the known characteristics of ML Kit's
Japanese recognizer and this app's deliberately simple MVP scope, not measurement. Treat
them as "expect these failure modes," not "we verified these fail this way."

## Known / expected limitations

- **Vertical Japanese text (縦書き).** The app detects predominantly tall text blocks and orders
  those columns right-to-left, but ML Kit's recognizer is still primarily designed for horizontal
  text. Characters may still be missed or grouped incorrectly.
- **Ruby / furigana.** Small annotation glyphs above or beside kanji are likely to be
  misread, merged into the base text, or dropped entirely. There is no furigana-aware
  parsing.
- **Reading order.** Horizontal blocks are ordered top-to-bottom and left-to-right; detected
  vertical columns are ordered right-to-left. Sidebars, footnotes, captions, and mixed layouts can
  still be ambiguous.
- **Page curvature and perspective.** User-guided perspective and curved-page correction are
  available, but the app does not automatically detect the page boundary or curvature.
- **Rotation.** EXIF orientation is normalized before display and OCR. Arbitrary camera skew still
  requires manual rotation/perspective correction.
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

Do not describe vertical text, furigana, or curved-page correction as high-accuracy until each has
been verified against a representative photographed-page corpus on a real device.
