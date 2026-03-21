package com.egginc.autoclicker.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import android.util.Log
import androidx.core.content.edit
import java.util.Locale

/**
 * FIXED LocaleHelper for AppCompat 1.6.1+
 * 
 * This version is designed to work with the LocaleDelegate wrapper approach.
 * It provides reliable locale switching on Android API 24-34.
 */
object LocaleHelper {
    
    const val LANGUAGE_RUSSIAN = "ru"
    const val LANGUAGE_ENGLISH = "en"
    
    private const val PREFS_NAME = "locale_prefs_v2"
    private const val KEY_LANGUAGE = "selected_language"
    
    private const val TAG = "LocaleHelper"

    /**
     * Gets the currently selected language code.
     * Returns empty string if using system default.
     */
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lang = prefs.getString(KEY_LANGUAGE, null) ?: ""
        Log.d(TAG, "getLanguage: $lang")
        return lang
    }
    
    /**
     * Check if current language is Russian.
     * Returns true for "ru" or when system locale is Russian and no explicit selection made.
     */
    fun isRussian(context: Context): Boolean {
        val savedLang = getLanguage(context)
        return when {
            savedLang == LANGUAGE_RUSSIAN -> true
            savedLang == LANGUAGE_ENGLISH -> false
            savedLang.isEmpty() -> {
                // No explicit selection - check system locale
                val systemLocale = getSystemLocale()
                systemLocale.language == LANGUAGE_RUSSIAN
            }
            else -> false
        }
    }
    
    /**
     * Get flag emoji for current language
     */
    fun getCurrentFlag(context: Context): String {
        return if (isRussian(context)) "🇷🇺" else "🇺🇸"
    }

    /**
     * Gets the effective locale (either saved or system default).
     */
    fun getEffectiveLocale(context: Context): Locale {
        val languageCode = getLanguage(context)
        
        return if (languageCode.isEmpty()) {
            getSystemLocale()
        } else {
            Locale(languageCode)
        }
    }

    /**
     * Changes the app's language and persists the selection.
     * 
     * @param context The context
     * @param languageCode The language code (e.g., "en", "ru") or empty string for system default
     * @return The context with new locale applied
     */
    fun changeLanguage(context: Context, languageCode: String): Context {
        Log.d(TAG, "changeLanguage to: $languageCode")
        
        // Save language selection
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_LANGUAGE, languageCode)
        }
        
        // Also save to ConfigManager for backup
        try {
            val configManager = ConfigManager(context)
            val config = configManager.loadConfig()
            config.appLanguage = languageCode
            configManager.saveConfig(config)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save to ConfigManager", e)
        }
        
        val locale = if (languageCode.isEmpty()) {
            getSystemLocale()
        } else {
            Locale(languageCode)
        }
        
        return applyLocale(context, locale)
    }

    /**
     * Applies the saved locale to a context.
     * Use this in Application.attachBaseContext() and Activity.attachBaseContext().
     */
    fun setLocale(context: Context): Context {
        val savedLang = getLanguage(context)
        val locale = getEffectiveLocale(context)
        Log.d(TAG, "setLocale: saved='$savedLang', effective='${locale.language}', isRussian=${isRussian(context)}")
        return applyLocale(context, locale)
    }

    /**
     * Applies locale to the given context.
     */
    @Suppress("DEPRECATION")
    fun applyLocale(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)

        val resources: Resources = context.resources
        val configuration: Configuration = resources.configuration

        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)

        val localeList = LocaleList(locale)
        LocaleList.setDefault(localeList)
        configuration.setLocales(localeList)

        return context.createConfigurationContext(configuration)
    }

    /**
     * Gets the system default locale.
     */
    fun getSystemLocale(): Locale {
        return Resources.getSystem().configuration.locales.get(0)
    }
}
