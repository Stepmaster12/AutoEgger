package com.egginc.autoclicker

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.egginc.autoclicker.utils.LocaleHelper
import com.egginc.autoclicker.utils.StringManager

/**
 * BaseApplication - инициализация приложения
 */
class BaseApplication : Application() {

    override fun attachBaseContext(base: Context) {
        val context = LocaleHelper.setLocale(base)
        super.attachBaseContext(context)
    }

    override fun onCreate() {
        super.onCreate()
        // Инициализируем язык
        StringManager.init(this)
    }

    /**
     * Re-apply locale when system configuration changes.
     * This handles cases like system language change while app is running.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Re-apply our custom locale over the system change
        applyLocale()
    }

    /**
     * Apply the saved locale to the application context.
     */
    private fun applyLocale() {
        val locale = LocaleHelper.getEffectiveLocale(this)
        java.util.Locale.setDefault(locale)
        
        // Update resources with the locale
        val resources = resources
        val configuration = Configuration(resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(android.os.LocaleList(locale))
        
        configuration.setLayoutDirection(locale)
        
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        
        createConfigurationContext(configuration)
    }
}
