package com.egginc.autoclicker.service

import android.content.Context

/**
 * Abstraction over AutoclickerService companion operations.
 * Used to reduce direct static coupling in UI/service layers.
 */
interface AutoclickerController {
    fun isRunning(): Boolean
    fun isPaused(): Boolean
    fun isPausedByOthers(owner: String): Boolean
    fun pause(owner: String = "legacy")
    fun resume(owner: String = "legacy")
    fun setMode(mode: ClickerMode)
    fun startResearch(context: Context? = null)
    fun stopResearch()
    fun setPauseStateListener(listener: ((Boolean) -> Unit)?)
}

object DefaultAutoclickerController : AutoclickerController {
    override fun isRunning(): Boolean = AutoclickerService.isRunning

    override fun isPaused(): Boolean = AutoclickerService.isPaused

    override fun isPausedByOthers(owner: String): Boolean = AutoclickerService.isPausedByOthers(owner)

    override fun pause(owner: String) = AutoclickerService.pause(owner)

    override fun resume(owner: String) = AutoclickerService.resume(owner)

    override fun setMode(mode: ClickerMode) {
        AutoclickerService.currentMode = mode
    }

    override fun startResearch(context: Context?) = AutoclickerService.startResearch(context)

    override fun stopResearch() = AutoclickerService.stopResearch()

    override fun setPauseStateListener(listener: ((Boolean) -> Unit)?) {
        AutoclickerService.onPauseStateChanged = listener
    }
}
