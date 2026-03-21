package com.egginc.autoclicker.service

import com.egginc.autoclicker.utils.ClickerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Отвечает только за логику проверки и активации бустов.
 */
class BoostRunner(
    private val reloadConfig: () -> Unit,
    private val configProvider: () -> ClickerConfig,
    private val updateScreenDimensions: suspend () -> Boolean,
    private val scaleX: (Int) -> Int,
    private val scaleY: (Int) -> Int,
    private val statusCheckingBoosts: () -> String
) {
    suspend fun runIteration(service: ClickerAccessibilityService) {
        reloadConfig()
        val cfg = configProvider()

        AutoclickerStatusBus.publish(statusCheckingBoosts())
        updateScreenDimensions()

        for (slot in cfg.boostSlots) {
            val slotX = scaleX(slot.x)
            val slotY = scaleY(slot.y)
            val activateX = scaleX(cfg.activateBoostButton.x)
            val activateY = scaleY(cfg.activateBoostButton.y)

            withContext(Dispatchers.Main) {
                service.performClick(slotX, slotY)
            }
            delay(500)

            withContext(Dispatchers.Main) {
                service.performClick(activateX, activateY)
            }
            delay(500)
        }
    }
}
