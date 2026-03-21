package com.egginc.autoclicker.service

import android.content.Context
import android.util.Log
import com.egginc.autoclicker.utils.ConfigManager

/**
 * Coordinates lifecycle of ResearchAutomationService.
 * Keeps creation/cleanup outside of AutoclickerService companion.
 */
object ResearchAutomationCoordinator {
    private const val TAG = "ResearchCoordinator"

    private val lock = Any()
    private var instance: ResearchAutomationService? = null

    fun start(context: Context? = null) {
        synchronized(lock) {
            if (instance?.isRunning() == true) {
                Log.d(TAG, "Research already running, ignoring start request")
                return
            }

            if (instance != null) {
                instance?.destroy()
                instance = null
                Log.d(TAG, "Old research instance cleaned up before restart")
            }

            ClickerAccessibilityService.instance?.let { accessibilityService ->
                val configManager = ConfigManager(context ?: accessibilityService)
                instance = ResearchAutomationService(accessibilityService, configManager)
                Log.d(TAG, "ResearchAutomationService created")
            } ?: run {
                Log.e(TAG, "Cannot start research - AccessibilityService not available")
                return
            }

            instance?.start()
            Log.d(TAG, "Research started")
        }
    }

    fun stop() {
        synchronized(lock) {
            Log.d(TAG, "Stopping research, instance exists: ${instance != null}")
            instance?.destroy()
            instance = null
        }
    }

    fun isRunning(): Boolean = synchronized(lock) { instance?.isRunning() == true }
}
