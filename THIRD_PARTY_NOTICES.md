# Third-party notices

The following components are used for the optional on-device Tesseract OCR engines. They are
licensed under the [Apache License 2.0](app/src/main/assets/licenses/Apache-2.0.txt).

| Component | Version/source | Usage |
| --- | --- | --- |
| Tesseract4Android | 4.9.0, https://github.com/adaptech-cz/Tesseract4Android | Android wrapper; includes Tesseract 5.5.1 |
| Tesseract OCR | 5.5.1, https://github.com/tesseract-ocr/tesseract | OCR engine |
| tessdata_fast | https://github.com/tesseract-ocr/tessdata_fast | Bundled Japanese language models |
| Android OCR reference app | https://github.com/SubhamTyagi/android-ocr | Architectural reference only; no source copied |

Bundled model integrity values:

- `jpn.traineddata`: `1F5DE9236D2E85F5FDF4B3C500F2D4926F8D9449F28F5394472D9E8D83B91B4D`
- `jpn_vert.traineddata`: `BF1E2640954691797E2DC14F38533E601B59EE37958698AE0F0B81DC6F09C71B`

The trained-data repositories state that the files are licensed under Apache-2.0. Preserve this
notice and the applicable license when redistributing the application or model files.
