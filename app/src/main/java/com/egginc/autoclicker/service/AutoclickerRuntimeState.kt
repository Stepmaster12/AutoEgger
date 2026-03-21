package com.egginc.autoclicker.service

import android.os.Handler
import android.os.Looper

/**
 * Centralized runtime state for autoclicker services.
 * Keeps cross-service mutable flags in one place to reduce tight coupling.
 */
object AutoclickerRuntimeState {

    @Volatile
    var isRunning: Boolean = false

    @Volatile
    var currentMode: ClickerMode = ClickerMode.ALL

    @Volatile
    var isPaused: Boolean = false

    @Volatile
    var onPauseStateChanged: ((Boolean) -> Unit)? = null

    private val pauseOwnersLock = Any()
    private val pauseOwners = linkedSetOf<String>()

    fun updatePauseState(paused: Boolean) {
        if (isPaused == paused) {
            return
        }
        isPaused = paused
        val callback = onPauseStateChanged ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(paused)
        } else {
            Handler(Looper.getMainLooper()).post {
                onPauseStateChanged?.invoke(paused)
            }
        }
    }

    fun acquirePause(owner: String) {
        val changed = synchronized(pauseOwnersLock) {
            val before = pauseOwners.isNotEmpty()
            pauseOwners.add(owner)
            val after = pauseOwners.isNotEmpty()
            before != after
        }
        if (changed) {
            updatePauseState(true)
        }
    }

    fun releasePause(owner: String) {
        val changed = synchronized(pauseOwnersLock) {
            val before = pauseOwners.isNotEmpty()
            pauseOwners.remove(owner)
            val after = pauseOwners.isNotEmpty()
            before != after
        }
        if (changed) {
            updatePauseState(false)
        }
    }

    fun clearPauseOwners() {
        val changed = synchronized(pauseOwnersLock) {
            val hadAny = pauseOwners.isNotEmpty()
            pauseOwners.clear()
            hadAny
        }
        if (changed) {
            updatePauseState(false)
        }
    }

    fun isPausedByOtherThan(owner: String): Boolean {
        return synchronized(pauseOwnersLock) {
            pauseOwners.any { it != owner }
        }
    }
}
