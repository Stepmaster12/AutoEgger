# Changelog

## v1.0.1 - 2026-03-22

### Changed
- Reduced runtime overhead by throttling debug logs in hot loops (research/gift/chicken/overlay paths).
- Improved auto-gift stability by tightening ROI and adding a distance gate around expected gift area.
- Improved coordination between automation services to reduce feature conflicts in parallel flows.

### Fixed
- Multiple false-positive and scaling edge cases for gift/chicken detection on low and high resolutions.
- Overlay/UI behavior and localization consistency issues in status labels and settings flow.

### Performance
- Removed unused dependencies and OpenCV native package usage from runtime path.
- Significantly reduced APK size (release build now ~5 MB range instead of triple-digit MB).
- Lower background noise from logs for smoother long-running sessions.

## v1.0.0 - 2026-03-22

Initial public release.

### Added
- Overlay-based control panel for automation modes.
- Auto Chicken (manual timings + smart red-zone pause mode).
- Auto Gift collection (CV mode + coordinate timer fallback).
- Auto Research automation with swipe and interval settings.
- Auto Boost 2x activation flow.
- Auto Drones swipe mode.
- Adaptive UI/scaling updates for different screen sizes.
- RU/EN localization and updated onboarding flow for Android 13+ permissions.

### Notes
- Android 13+ may require enabling `Allow restricted settings` before Accessibility can be enabled.
- CV features can use more CPU on weak devices.
