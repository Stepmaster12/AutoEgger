#!/bin/bash

echo "=========================================="
echo "   Egg Inc Autoclicker - Сборка APK"
echo "=========================================="
echo ""

# Проверяем наличие gradlew
if [ ! -f "./gradlew" ]; then
    echo "Создание Gradle Wrapper..."
    gradle wrapper
fi

echo ""
echo "Запуск сборки..."
echo ""

# Собираем debug APK
./gradlew assembleDebug

if [ $? -ne 0 ]; then
    echo ""
    echo "ОШИБКА СБОРКИ!"
    echo "Проверьте подключение к интернету и попробуйте снова."
    read -p "Нажмите Enter для выхода..."
    exit 1
fi

echo ""
echo "=========================================="
echo "   Сборка завершена успешно!"
echo "=========================================="
echo ""
echo "APK файл находится здесь:"
echo "app/build/outputs/apk/debug/app-debug.apk"
echo ""
echo "Чтобы установить на телефон:"
echo "1. Включите 'Отладку по USB' на телефоне"
echo "2. Подключите телефон к компьютеру"
echo "3. Выполните: adb install app/build/outputs/apk/debug/app-debug.apk"
echo ""
read -p "Нажмите Enter для выхода..."
