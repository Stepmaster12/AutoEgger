@echo off
chcp 65001 >nul
echo ==========================================
echo   Egg Inc Autoclicker - Сборка APK
echo ==========================================
echo.

:: Проверяем наличие gradlew
if not exist "gradlew.bat" (
    echo Создание Gradle Wrapper...
    call gradle wrapper
)

echo.
echo Запуск сборки...
echo.

:: Собираем debug APK
call gradlew.bat assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ОШИБКА СБОРКИ!
    echo Проверьте подключение к интернету и попробуйте снова.
    pause
    exit /b 1
)

echo.
echo ==========================================
echo   Сборка завершена успешно!
echo ==========================================
echo.
echo APK файл находится здесь:
echo app\build\outputs\apk\debug\app-debug.apk
echo.
echo Чтобы установить на телефон:
echo 1. Включите "Отладку по USB" на телефоне
echo 2. Подключите телефон к компьютеру
echo 3. Выполните: adb install app\build\outputs\apk\debug\app-debug.apk
echo.
pause
