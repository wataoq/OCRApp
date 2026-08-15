# Tesseract 5 migration plan

## Decision

Use the F-Droid app `SubhamTyagi/android-ocr` as a reference implementation, not as source to copy
wholesale. Integrate its underlying Apache-2.0 engine,
[`Tesseract4Android`](https://github.com/adaptech-cz/Tesseract4Android), behind BookOCR's existing
`OcrEngine` interface. This preserves the current UI, Room schema, edit-safety invariant, and image
pipeline while allowing ML Kit and Tesseract to be compared on the same input file.

## Proposed first experiment

1. Add a `TesseractOcrEngine` implementation using one long-lived, serialized `TessBaseAPI` instance.
2. Test both official Apache-2.0 language models:
   - `jpn.traineddata` for horizontal Japanese.
   - `jpn_vert.traineddata` for vertical Japanese.
3. Start with `tessdata_fast` for iteration speed, then benchmark `tessdata_best` on the same pages.
4. Add an explicit engine/layout selector to the editor; do not silently replace existing ML Kit
   results or user edits.
5. Record engine id, language model, page segmentation mode, duration, and confidence metadata
   without storing or logging page text outside the existing private database.

## Training-data distribution decision

The current app deliberately has no `INTERNET` permission. Preserve that privacy posture by
bundling the selected Japanese model(s) in the APK for the first experiment. A later optional
download manager can reduce APK size, but it would require a new network/privacy decision,
integrity hashes, resumable downloads, and failure-state UI.

## Accuracy evaluation

Create a small private corpus representing:

- horizontal and vertical Japanese;
- ruby/furigana and small print;
- flat, perspective-distorted, and curved pages;
- original, perspective-corrected, and dewarped variants.

For each page, compare ML Kit, Tesseract `jpn`, and Tesseract `jpn_vert` using character error rate,
processing time, peak memory, and subjective reading-order correctness. The engine should be chosen
from measured results rather than replaced globally before this comparison.

## License obligations

Tesseract, its official trained data, Tesseract4Android, and the reference F-Droid app are
Apache-2.0 licensed. Retain required copyright/license notices and add the dependency and trained
data to the app's open-source notices before distribution.
