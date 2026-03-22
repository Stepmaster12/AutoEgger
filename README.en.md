# AutoEgger

<p align="center">
  <a href="./README.ru.md">
    <img src="https://img.shields.io/badge/Язык-Русский-F97316?style=for-the-badge" alt="Russian README">
  </a>
  <a href="./README.md">
    <img src="https://img.shields.io/badge/Main-README-111827?style=for-the-badge" alt="Main README">
  </a>
</p>

Android app for automating routine actions in **Egg Inc**  
Main goal: automate routine gameplay as much as possible so you can leave the phone overnight and wake up to a big progress jump.

## Highlights

- Adaptive behavior for different screen sizes and aspect ratios
- CV-assisted automation for gifts/research workflows
- Overlay controls with independent auto-features

## Important

This project was actively developed with AI assistance.  
If you want to continue the work or improve the app, please create a fork and publish your changes there instead of sending direct patches.

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
- Android 13+ with restricted settings enabled for Accessibility

### Tested Devices
- Samsung S24 Ultra (real device: 4K + 1560x720 (HD+) tests)
- Android Studio phone emulator (small resolution)
- Android Studio tablet emulator (wide screen)

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

MIT. See [LICENSE](./LICENSE).

