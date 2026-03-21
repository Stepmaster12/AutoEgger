package com.egginc.autoclicker.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

class PermissionHelper(private val context: Context) {
    
    /**
     * Проверяет, есть ли разрешение на отрисовку поверх других приложений
     */
    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }
    
    /**
     * Проверяет, включен ли Accessibility Service
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        
        for (service in enabledServices) {
            val id = service.id
            if (id.contains(context.packageName) && id.contains("ClickerAccessibilityService")) {
                return true
            }
        }
        
        // Альтернативная проверка через Settings
        try {
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            
            return enabledServicesSetting.contains(context.packageName)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return false
    }
}
