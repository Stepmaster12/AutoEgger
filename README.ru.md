# AutoEgger

<p align="center">
  <a href="./README.en.md">
    <img src="https://img.shields.io/badge/Language-English-2563EB?style=for-the-badge" alt="English README">
  </a>
  <a href="./README.md">
    <img src="https://img.shields.io/badge/Main-README-111827?style=for-the-badge" alt="Main README">
  </a>
</p>

<p align="center">
  <a href="https://github.com/Stepmaster12/AutoEgger/releases">
    <img src="https://img.shields.io/badge/Скачать-Latest%20APK-22C55E?style=for-the-badge&logo=android&logoColor=white" alt="Скачать Latest APK">
  </a>
</p>

Android-приложение для автоматизации рутины в **Egg Inc**  
Основной фокус это максимально автоматизировать все рутинные задачи, чтобы можно было спокойно оставить телефон на ночь и наслаждаться на следующее утро карманами, полными яиц.

## Ключевые моменты

- Адаптивное поведение для разных экранов и соотношений сторон
- CV-автоматизация для подарков и исследований
- Оверлей с независимыми авто-функциями

## Важно

Проект активно дорабатывался с помощью ИИ.  
Если хотите продолжить развитие или сделать лучше, пожалуйста, создайте форк и ведите изменения в своём репозитории.

Поток разрешений на Android 13+:
1. Выдайте Overlay.
2. Откройте Accessibility и один раз нажмите сервис `Egg Inc Autoclicker` (чтобы появилось предупреждение restricted settings).
3. Откройте настройки приложения, нажмите `⋮` (справа сверху), выберите `Allow restricted settings`.
4. Вернитесь в Accessibility и включите сервис.

<p align="center"><img src="./docs/permissions_flow.gif" alt="Поток разрешений Android 13+" width="360"></p>

## Совместимость

### Минимальная поддержка
- Android 7.0+ (API 24+)
- Разрешение на оверлей
- Включённый Accessibility Service

### Рекомендуется
- Android 11+ для CV-сценариев
- Android 13+ после включения restricted settings для Accessibility

### Проверенные устройства
- Samsung S24 Ultra (реальное устройство: тесты в 4K и 1560x720 (HD+))
- Эмулятор телефона в Android Studio (малое разрешение)
- Эмулятор планшета в Android Studio (широкий экран)

## Возможности

- `Авто-курицы`: обычный и умный режим (пауза при красной зоне)

<p align="center"><img src="./docs/gifs/auto_chicken.gif" alt="Демо авто-куриц" width="360"></p>

- `Авто-подарки`: CV-поиск объекта + таймерный режим

<p align="center"><img src="./docs/gifs/auto_gift.gif" alt="Демо авто-подарков" width="360"></p>

- `Авто-исследования`: поиск зелёных кнопок, свайпы, интервал

<p align="center"><img src="./docs/gifs/auto_research.gif" alt="Демо авто-исследований" width="360"></p>

- `Авто-буст 2x`: автоприменение Video Doubler по интервалу

<p align="center"><img src="./docs/gifs/auto_boost.gif" alt="Демо авто-буста" width="360"></p>

- `Авто-дроны`: свайповый режим ловли

<p align="center"><img src="./docs/gifs/auto_drones.gif" alt="Демо авто-дронов" width="360"></p>

## Скриншоты

Главный экран:

<p align="center"><img src="./docs/images/main-screen.png" alt="Главный экран" width="360"></p>

Экран настроек:

<p align="center"><img src="./docs/images/settings-screen.png" alt="Экран настроек" width="360"></p>

Панель авто-функций (развёрнута):

<p align="center"><img src="./docs/images/settings-auto-features-expanded.png" alt="Развёрнутые авто-функции" width="360"></p>

## Быстрый старт

1. Установите APK из [Releases](https://github.com/Stepmaster12/AutoEgger/releases).
2. Выдайте приложению разрешения (оверлей + accessibility).
3. Откройте настройки и проверьте интервалы/режимы.
4. Запустите оверлей и нажмите `START`.

## Дисклеймер

Использование на ваш страх и риск. Ну вы и так знаете, чё мне об этом говорить.

## Структура проекта

- `app/src/main/java/com/egginc/autoclicker/`  
  Основной код приложения (UI, сервисы, логика автоматизации).
- `app/src/main/java/com/egginc/autoclicker/service/`  
  Сервисы выполнения и раннеры автоматизации.
- `app/src/main/java/com/egginc/autoclicker/cv/`  
  CV-хелперы для детекта объектов.
- `app/src/main/res/`  
  Layout, строки, drawables и локализация.

## Технологии

- Kotlin + Android SDK
- Accessibility Service + Overlay window
- Лёгкий bitmap/template CV-детект (без OpenCV runtime зависимости)
- Coroutines для фоновых задач

## Лицензия

MIT. См. [LICENSE](./LICENSE).

