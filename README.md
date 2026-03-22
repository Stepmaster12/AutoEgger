# AutoEgger

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/Min%20API-24-2563EB?style=for-the-badge" alt="Min API">
  <img src="https://img.shields.io/badge/License-MIT-F97316?style=for-the-badge" alt="MIT License">
</p>

<p align="center">
  <a href="./README.ru.md">
    <img src="https://img.shields.io/badge/Русский-README-F97316?style=for-the-badge" alt="README Russian">
  </a>
  <a href="./README.en.md">
    <img src="https://img.shields.io/badge/English-README-2563EB?style=for-the-badge" alt="README English">
  </a>
</p>

Android app for automating routine actions in **Egg Inc**  
Main goal: automate as much routine as possible so the game can safely run for long sessions with minimal manual input.

## Highlights

- Adaptive behavior for different screen sizes and aspect ratios
- CV-assisted automation for gifts/research workflows
- Overlay controls with independent auto-features

## Important

This project was actively developed with AI assistance.  
If you want to continue or improve it, please fork the repository and publish your changes in your own fork.

Android 13+ permission flow:
1. Grant Overlay.
2. Open Accessibility and tap the `Egg Inc Autoclicker` service once (to trigger the restricted-settings warning).
3. Open App Settings, tap `⋮` (top-right), choose `Allow restricted settings`.
4. Return to Accessibility and enable the service.

![Android 13+ permission walkthrough](./docs/permissions_flow.gif)

## Compatibility

### Minimum Supported
- Android 7.0+ (API 24+)
- Overlay permission
- Accessibility Service enabled

### Recommended
- Android 11+ for CV-heavy scenarios
- Android 13+ works best after completing restricted-settings flow for Accessibility

### Tested Devices
- Samsung S24 Ultra (real device: 4K + 1560x720 (HD+) tests)
- Android Studio phone emulator (small-resolution profile)
- Android Studio tablet emulator (wide-screen profile)

## Features

- `Auto Chicken`: normal mode and smart pause on red zone

![Auto Chicken demo](./docs/gifs/auto_chicken.gif)

- `Auto Gift`: CV object detection + timer-based fallback

![Auto Gift demo](./docs/gifs/auto_gift.gif)

- `Auto Research`: green-button scanning, swipes, interval

![Auto Research demo](./docs/gifs/auto_research.gif)

- `Auto Boost 2x`: scheduled Video Doubler activation

![Auto Boost demo](./docs/gifs/auto_boost.gif)

- `Auto Drones`: swipe-based catch routine

![Auto Drones demo](./docs/gifs/auto_drones.gif)

## Screenshots

Main screen:

![Main screen](./docs/images/main-screen.png)

Settings screen:

![Settings screen](./docs/images/settings-screen.png)

Auto-features panel (expanded):

![Auto-features expanded](./docs/images/settings-auto-features-expanded.png)

## Quick Start

1. Install APK.
2. Grant overlay and accessibility permissions.
3. Open settings and tune intervals/modes.
4. Launch overlay and press `START`.

## Disclaimer

Use at your own risk.

## Project Structure

- `app/src/main/java/com/egginc/autoclicker/`  
  Core app code (UI, services, automation logic).
- `app/src/main/java/com/egginc/autoclicker/service/`  
  Runtime services and automation runners.
- `app/src/main/java/com/egginc/autoclicker/cv/`  
  Computer vision helpers for detection workflows.
- `app/src/main/res/`  
  Layouts, strings, drawables and localization resources.

## Tech Stack

- Kotlin + Android SDK
- Accessibility Service + Overlay window
- Lightweight bitmap/template CV detection (no OpenCV runtime dependency)
- Coroutines for async/background tasks

## License

MIT License. See [LICENSE](LICENSE).

