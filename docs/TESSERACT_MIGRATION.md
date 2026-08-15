# Tesseract 5 migration plan

## Decision

Use the F-Droid app `SubhamTyagi/android-ocr` as a reference implementation, not as source to copy
wholesale. Integrate its underlying Apache-2.0 engine,
[`Tesseract4Android`](https://github.com/adaptech-cz/Tesseract4Android), behind BookOCR's existing
`OcrEngine` interface. This preserves the current UI, Room schema, edit-safety invariant, and image
pipeline while allowing ML Kit and Tesseract to be compared on the same input file.

## Implemented first experiment

- `Tesseract4Android` 4.9.0 (Tesseract 5.5.1) is registered alongside ML Kit.
- Official `tessdata_fast` `jpn` and `jpn_vert` models are bundled and verified by SHA-256 before
  being installed atomically in app-private storage.
- The editor exposes explicit ML Kit, horizontal Tesseract, and vertical Tesseract choices.
- Engine changes go through the existing safe rerun merge, so a user's corrections are preserved.
- Each recognition call owns and recycles its `TessBaseAPI` instance, avoiding unsafe concurrent
  access when multiple pages are processed.

## Remaining accuracy experiment

1. Benchmark `tessdata_fast` against `tessdata_best` on the same private page corpus.
2. Tune page segmentation and preprocessing separately for horizontal and vertical pages.
3. Record engine id, language model, page segmentation mode, duration, and confidence metadata
   without logging or exporting page text.
4. Keep ML Kit as the default until measurements justify changing it.

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
Apache-2.0 licensed. Their sources, versions, and model hashes are recorded in
[`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md).
