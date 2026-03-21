package com.egginc.autoclicker.utils

import android.content.Context

/**
 * Простой менеджер строк для переключения между русским и английским.
 * НЕ использует системные ресурсы Android - все строки хранятся здесь.
 */
object StringManager {
    
    private var currentLanguage: String = "ru"
    
    // Используются только ключи, реально запрашиваемые через StringManager.get/format.
    private val strings = mapOf(
        "app_name" to mapOf(
            "ru" to "Egg Inc Autoclicker",
            "en" to "Egg Inc Autoclicker"
        ),
        "start" to mapOf(
            "ru" to "▶ ЗАПУСК",
            "en" to "▶ START"
        ),
        "restart_overlay" to mapOf(
            "ru" to "🔄 Перезапустить",
            "en" to "🔄 Restart"
        ),
        "grant" to mapOf(
            "ru" to "Предоставить",
            "en" to "Grant"
        ),
        "granted" to mapOf(
            "ru" to "✓ Готово",
            "en" to "✓ Ready"
        ),
        "overlay_on" to mapOf(
            "ru" to "Оверлей: ✅",
            "en" to "Overlay: ✅"
        ),
        "overlay_off" to mapOf(
            "ru" to "Оверлей: ❌",
            "en" to "Overlay: ❌"
        ),
        "accessibility_on" to mapOf(
            "ru" to "Доступность: ✅",
            "en" to "Accessibility: ✅"
        ),
        "accessibility_off" to mapOf(
            "ru" to "Доступность: ❌",
            "en" to "Accessibility: ❌"
        ),
        "settings_title" to mapOf(
            "ru" to "Настройки",
            "en" to "Settings"
        ),
        "auto_research" to mapOf(
            "ru" to "🔬 Авто-исследования",
            "en" to "🔬 Auto Research"
        ),
        "auto_boost2x" to mapOf(
            "ru" to "⚡ Авто-буст 2x",
            "en" to "⚡ Auto Boost 2x"
        ),
        "auto_drones" to mapOf(
            "ru" to "✈ Авто-дроны",
            "en" to "✈ Auto Drones"
        ),
        "research_fast_mode" to mapOf(
            "ru" to "Быстрый режим исследований",
            "en" to "Fast research mode"
        ),
        "reset_settings" to mapOf(
            "ru" to "↺ Сбросить",
            "en" to "↺ Reset"
        ),
        "save" to mapOf(
            "ru" to "💾 СОХРАНИТЬ",
            "en" to "💾 SAVE"
        ),
        "exit" to mapOf(
            "ru" to "◄ НАЗАД",
            "en" to "◄ BACK"
        ),
        "dialog_reset_title" to mapOf(
            "ru" to "Сброс настроек",
            "en" to "Reset Settings"
        ),
        "dialog_reset_confirm" to mapOf(
            "ru" to "Сбросить",
            "en" to "Reset"
        ),
        "dialog_cancel" to mapOf(
            "ru" to "Отмена",
            "en" to "Cancel"
        ),
        "language" to mapOf(
            "ru" to "Язык / Language",
            "en" to "Language / Язык"
        ),
        "language_ru" to mapOf(
            "ru" to "Русский",
            "en" to "Russian"
        ),
        "language_en" to mapOf(
            "ru" to "English",
            "en" to "English"
        ),
        "language_switch_message" to mapOf(
            "ru" to "Переключить на %s?\n\nПриложение перезапустится.",
            "en" to "Switch to %s?\n\nApp will restart."
        ),
        "current_gift_interval" to mapOf(
            "ru" to "Текущий интервал подарков: %d сек (задаётся в главном меню)",
            "en" to "Current gift interval: %d sec (set in main menu)"
        ),
    )
    
    /**
     * Инициализация при старте приложения
     */
    fun init(context: Context) {
        loadLanguage(context)
    }
    
    /**
     * Загрузить сохранённый язык
     */
    fun loadLanguage(context: Context) {
        currentLanguage = LocaleHelper.getLanguage(context)
        if (currentLanguage.isEmpty()) {
            // Если не выбран явно - используем системный
            val systemLocale = LocaleHelper.getSystemLocale()
            currentLanguage = if (systemLocale.language == "ru") "ru" else "en"
        }
        // Только ru или en
        if (currentLanguage != "ru" && currentLanguage != "en") {
            currentLanguage = "en"
        }
    }
    
    /**
     * Получить строку на текущем языке
     */
    fun get(key: String): String {
        return strings[key]?.get(currentLanguage) 
            ?: strings[key]?.get("en") 
            ?: strings[key]?.get("ru")
            ?: key
    }
    
    /**
     * Получить строку с форматированием
     */
    fun format(key: String, vararg args: Any): String {
        return try {
            String.format(get(key), *args)
        } catch (e: Exception) {
            get(key)
        }
    }
    
    /**
     * Проверить, русский ли язык
     */
    fun isRussian(): Boolean = currentLanguage == "ru"
    
    /**
     * Получить флаг для кнопки
     */
    fun getCurrentFlag(): String = if (isRussian()) "🇷🇺" else "🇺🇸"
    
    /**
     * Получить язык для переключения
     */
    @Suppress("unused")
    fun getOtherLanguage(): String = if (isRussian()) "en" else "ru"
    
    /**
     * Получить название языка для диалога
     */
    fun getLanguageName(lang: String): String {
        return when (lang) {
            "ru" -> get("language_ru")
            "en" -> get("language_en")
            else -> lang
        }
    }
}


