# Shahab Scanner

A Persian-first document scanning and multi-page project manager for Android and Windows.

## Features
- Android document scanning with Google ML Kit Document Scanner.
- Separate CameraX capture path to preserve captured images independently from edits.
- Multi-page workflow, rotate/grayscale/restore, PDF export and OCR text export.
- `.shahab` archive with original image, edited image and OCR text per page.
- Windows desktop app based on Python, PySide6, OpenCV, Pillow and Tesseract.

## Build status
GitHub Actions builds a debug/test APK and a Windows package. A GitHub Release is created when a `v*` tag is pushed. Android builds are unsigned debug builds and are not suitable for Play Store distribution without signing.

## Build locally
- Windows: `python -m pip install -r windows/requirements.txt` then `python windows/main.py`.
- Android: open `android/` in Android Studio with JDK 17 and Android SDK 35.

## Releases
See [GitHub Releases](../../releases). Build artifacts from branch runs are available in Actions.

Read [README_FA.md](README_FA.md) for the Persian guide and [docs/RAW_CAPTURE_FA.md](docs/RAW_CAPTURE_FA.md) for raw image capture details.
