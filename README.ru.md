# AutoEgger

<p align="center">
  <a href="./README.en.md">
    <img src="https://img.shields.io/badge/Language-English-2563EB?style=for-the-badge" alt="English README">
  </a>
  <a href="./README.md">
    <img src="https://img.shields.io/badge/Main-README-111827?style=for-the-badge" alt="Main README">
  </a>
</p>

Android-приложение для автоматизации рутины в **Egg Inc**  
Основной фокус это максимально автоматизировать все рутинные задачи, чтобы можно было спокойно оставить телефон на ночь и наслаждаться на следующее утро карманами, полными яиц.

## Highlights

- Адаптивное поведение для разных экранов и соотношений сторон
- CV-автоматизация для подарков и исследований
- Оверлей с независимыми авто-функциями

## Important

Проект активно дорабатывался с помощью ИИ.  
Если хотите продолжить развитие или сделать лучше, пожалуйста, создайте форк и ведите изменения в своём репозитории.

Поток разрешений на Android 13+:
1. Выдайте Overlay.
2. Откройте Accessibility и один раз нажмите сервис `Egg Inc Autoclicker` (чтобы появилось предупреждение restricted settings).
3. Откройте настройки приложения, нажмите `⋮` (справа сверху), выберите `Allow restricted settings`.
4. Вернитесь в Accessibility и включите сервис.

![Поток разрешений Android 13+](./docs/permissions_flow.gif)

## Compatibility

### Minimum Supported
- Android 7.0+ (API 24+)
- Разрешение на оверлей
- Включённый Accessibility Service

### Recommended
- Android 11+ для CV-сценариев
- Android 13+ после включения restricted settings для Accessibility

### Tested Devices
- Samsung S24 Ultra (реальное устройство: тесты в 4K и 1560x720 (HD+))
- Эмулятор телефона в Android Studio (малое разрешение)
- Эмулятор планшета в Android Studio (широкий экран)

## Features

- `Авто-курицы`: обычный и умный режим (пауза при красной зоне)

![Демо авто-куриц](./docs/gifs/auto_chicken.gif)

- `Авто-подарки`: CV-поиск объекта + таймерный режим

![Демо авто-подарков](./docs/gifs/auto_gift.gif)

- `Авто-исследования`: поиск зелёных кнопок, свайпы, интервал

![Демо авто-исследований](./docs/gifs/auto_research.gif)

- `Авто-буст 2x`: автоприменение Video Doubler по интервалу

![Демо авто-буста](./docs/gifs/auto_boost.gif)

- `Авто-дроны`: свайповый режим ловли

![Демо авто-дронов](./docs/gifs/auto_drones.gif)

## Screenshots

Главный экран:

![Главный экран](./docs/images/main-screen.png)

Экран настроек:

![Экран настроек](./docs/images/settings-screen.png)

Панель авто-функций (развёрнута):

![Развёрнутые авто-функции](./docs/images/settings-auto-features-expanded.png)

## Quick Start

1. Установите APK.
2. Выдайте приложению разрешения (оверлей + accessibility).
3. Откройте настройки и проверьте интервалы/режимы.
4. Запустите оверлей и нажмите `START`.

## Disclaimer

Использование на ваш страх и риск. Ну вы и так знаете, чё мне об этом говорить.

## Project Structure

- `app/src/main/java/com/egginc/autoclicker/`  
  Основной код приложения (UI, сервисы, логика автоматизации).
- `app/src/main/java/com/egginc/autoclicker/service/`  
  Сервисы выполнения и раннеры автоматизации.
- `app/src/main/java/com/egginc/autoclicker/cv/`  
  CV-хелперы для детекта объектов.
- `app/src/main/res/`  
  Layout, строки, drawables и локализация.

## Tech Stack

- Kotlin + Android SDK
- Accessibility Service + Overlay window
- Лёгкий bitmap/template CV-детект (без OpenCV runtime зависимости)
- Coroutines для фоновых задач

## License

MIT. См. [LICENSE](./LICENSE).

