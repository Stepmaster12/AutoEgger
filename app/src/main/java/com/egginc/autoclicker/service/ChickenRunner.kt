package com.egginc.autoclicker.service

import android.os.Build
import android.util.Log
import androidx.core.graphics.get
import com.egginc.autoclicker.utils.ClickerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Отвечает только за логику фарма куриц.
 */
class ChickenRunner(
    private val reloadConfig: () -> Unit,
    private val configProvider: () -> ClickerConfig,
    private val isPausedProvider: () -> Boolean,
    private val updateScreenDimensions: suspend () -> Boolean,
    private val scaleX: (Int) -> Int,
    private val scaleY: (Int) -> Int,
    private val setScreenDimensions: (Int, Int) -> Unit,
    private val screenSizeProvider: () -> Pair<Int, Int>,
    private val screenshotsWorkingProvider: () -> Boolean,
    private val statusChickenFarming: () -> String,
    private val statusResting: () -> String,
    private val statusCheckingIndicator: () -> String,
    private val statusRedZone: (Int) -> String
) {
    companion object {
        private const val TAG = "ChickenRunner"
    }

    suspend fun runIteration(service: ClickerAccessibilityService) {
        if (isPausedProvider()) return
        val cfg = configProvider()
        val canUseSmartMode =
            cfg.smartChickenMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && screenshotsWorkingProvider()

        if (canUseSmartMode) {
            doSmartChickenFarming(service)
        } else {
            doManualChickenFarming(service)
        }
    }

    private suspend fun doManualChickenFarming(service: ClickerAccessibilityService) {
        if (isPausedProvider()) return
        reloadConfig()
        updateScreenDimensions()
        val cfg = configProvider()

        AutoclickerStatusBus.publish(statusChickenFarming())

        val swipeDuration = cfg.chickenSwipeDurationMs
        val restDuration = cfg.chickenRestDurationMs

        val startX = scaleX(cfg.chickenButton.x)
        val startY = scaleY(cfg.chickenButton.y)
        val endX = scaleX(cfg.chickenButton.x + 30)
        val (screenWidth, screenHeight) = screenSizeProvider()

        Log.d(
            TAG,
            "Manual chicken: screen=${screenWidth}x$screenHeight, base=${cfg.baseResolutionWidth}x${cfg.baseResolutionHeight}"
        )
        Log.d(
            TAG,
            "Manual chicken: original=(${cfg.chickenButton.x},${cfg.chickenButton.y}) -> scaled=($startX,$startY)"
        )

        withContext(Dispatchers.Main) {
            if (isPausedProvider()) return@withContext
            service.performSlowSwipe(startX, startY, endX, startY, swipeDuration)
        }

        delay(swipeDuration)

        AutoclickerStatusBus.publish(statusResting())
        delay(restDuration)
    }

    private suspend fun doSmartChickenFarming(service: ClickerAccessibilityService) {
        if (isPausedProvider()) return
        reloadConfig()
        updateScreenDimensions()
        val cfg = configProvider()

        val isRedZone = checkRedIndicator(service)

        Log.d(
            TAG,
            "Smart chicken: isRedZone=$isRedZone, checkPoint=(${cfg.redIndicatorCheckPoint.x},${cfg.redIndicatorCheckPoint.y})"
        )

        if (isRedZone) {
            val cooldownMs = cfg.redIndicatorCooldownMs
            Log.d(TAG, "Red zone detected! Cooling down for ${cooldownMs}ms")
            AutoclickerStatusBus.publish(statusRedZone((cooldownMs / 1000).toInt()))
            delay(cooldownMs)
            return
        }

        AutoclickerStatusBus.publish(statusChickenFarming())

        val swipeDuration = cfg.chickenSwipeDurationMs
        val startX = scaleX(cfg.chickenButton.x)
        val startY = scaleY(cfg.chickenButton.y)
        val endX = scaleX(cfg.chickenButton.x + 30)

        withContext(Dispatchers.Main) {
            if (isPausedProvider()) return@withContext
            service.performSlowSwipe(startX, startY, endX, startY, swipeDuration)
        }

        delay(swipeDuration)
        AutoclickerStatusBus.publish(statusCheckingIndicator())
        delay(1000)
    }

    /**
     * Проверяет красную полосу индикатора.
     */
    private suspend fun checkRedIndicator(service: ClickerAccessibilityService): Boolean {
        return try {
            withTimeout(3000) {
                val bitmap = service.takeScreenshotSuspend()
                    ?: return@withTimeout false

                try {
                    setScreenDimensions(bitmap.width, bitmap.height)
                    val cfg = configProvider()
                    val baseX = scaleX(cfg.redIndicatorCheckPoint.x)
                    val baseY = scaleY(cfg.redIndicatorCheckPoint.y)
                    val baseShortEdge = min(cfg.baseResolutionWidth, cfg.baseResolutionHeight).coerceAtLeast(1)
                    val screenShortEdge = min(bitmap.width, bitmap.height).coerceAtLeast(1)
                    val scaleFactor = (screenShortEdge.toFloat() / baseShortEdge.toFloat()).coerceIn(0.35f, 2.5f)

                    val tolerance = max(cfg.colorTolerance, 26)
                    val targetRed = cfg.colorRedIndicator.r
                    val targetGreen = cfg.colorRedIndicator.g
                    val targetBlue = cfg.colorRedIndicator.b
                    val halfWidth = max(14, (screenShortEdge * 0.04f).toInt())
                    val halfHeight = max(5, (screenShortEdge * 0.012f).toInt())
                    val step = if (screenShortEdge <= 900) 2 else 3

                    val left = (baseX - halfWidth).coerceIn(0, bitmap.width - 1)
                    val right = (baseX + halfWidth).coerceIn(left, bitmap.width - 1)
                    val top = (baseY - halfHeight).coerceIn(0, bitmap.height - 1)
                    val bottom = (baseY + halfHeight).coerceIn(top, bitmap.height - 1)

                    var redCount = 0
                    var greenCount = 0
                    var sampled = 0
                    var bestHit = 0

                    for (y in top..bottom step step) {
                        for (x in left..right step step) {
                            sampled++
                            val pixel = bitmap[x, y]
                            val r = android.graphics.Color.red(pixel)
                            val g = android.graphics.Color.green(pixel)
                            val b = android.graphics.Color.blue(pixel)

                            val nearTarget = abs(r - targetRed) <= tolerance &&
                                abs(g - targetGreen) <= tolerance &&
                                abs(b - targetBlue) <= tolerance
                            val redDominant = r >= 165 && r > g + 52 && r > b + 52 && g <= 130 && b <= 130
                            val greenDominant = g >= 145 && g > r + 24 && g > b + 24

                            if (nearTarget || redDominant) {
                                redCount++
                                val score = (r - g) + (r - b)
                                if (score > bestHit) bestHit = score
                            }
                            if (greenDominant) {
                                greenCount++
                            }
                        }
                    }

                    val ratio = if (sampled > 0) redCount.toFloat() / sampled.toFloat() else 0f
                    val greenRatio = if (sampled > 0) greenCount.toFloat() / sampled.toFloat() else 0f
                    val minHits = if (screenShortEdge <= 900) 3 else 6
                    val minRatio = if (screenShortEdge <= 900) 0.012f else 0.020f
                    val likelyGreenState = greenCount > redCount && greenRatio >= 0.18f
                    // Локальная зона маленькая: прежний порог 2.5% был слишком строгим на малых экранах.
                    val localRed = !likelyGreenState && redCount >= minHits && ratio >= minRatio && bestHit >= 170
                    val fallbackRed = if (!localRed && !likelyGreenState) {
                        detectRedInTopBand(bitmap, baseX, screenShortEdge, targetRed, targetGreen, targetBlue, tolerance)
                    } else {
                        false
                    }
                    val isRed = localRed || fallbackRed
                    Log.d(
                        TAG,
                        "Red indicator check: base=(${cfg.redIndicatorCheckPoint.x},${cfg.redIndicatorCheckPoint.y}) " +
                            "scaled=($baseX,$baseY), rect=[$left,$top]-[$right,$bottom], " +
                            "screen=${bitmap.width}x${bitmap.height}, scale=${"%.3f".format(scaleFactor)}, " +
                            "tolerance=$tolerance, hits=$redCount/$sampled (${String.format("%.4f", ratio)}), " +
                            "green=$greenCount (${String.format("%.4f", greenRatio)}), likelyGreen=$likelyGreenState, " +
                            "bestHit=$bestHit, localRed=$localRed, fallbackRed=$fallbackRed, isRed=$isRed"
                    )
                    isRed
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (_: TimeoutCancellationException) {
            Log.w(TAG, "Screenshot timeout")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking red indicator", e)
            false
        }
    }

    private fun detectRedInTopBand(
        bitmap: android.graphics.Bitmap,
        baseX: Int,
        screenShortEdge: Int,
        targetRed: Int,
        targetGreen: Int,
        targetBlue: Int,
        tolerance: Int
    ): Boolean {
        val halfBandWidth = max(90, (screenShortEdge * 0.14f).toInt())
        val left = (baseX - halfBandWidth).coerceIn(0, bitmap.width - 1)
        val right = (baseX + halfBandWidth).coerceIn(left, bitmap.width - 1)
        val top = (bitmap.height * 0.08f).toInt().coerceIn(0, bitmap.height - 1)
        val bottom = (bitmap.height * 0.20f).toInt().coerceIn(top, bitmap.height - 1)
        val step = if (screenShortEdge <= 900) 6 else 7

        var redCount = 0
        var sampled = 0
        for (y in top..bottom step step) {
            for (x in left..right step step) {
                sampled++
                val p = bitmap[x, y]
                val r = android.graphics.Color.red(p)
                val g = android.graphics.Color.green(p)
                val b = android.graphics.Color.blue(p)

                val nearTarget = abs(r - targetRed) <= tolerance &&
                    abs(g - targetGreen) <= tolerance &&
                    abs(b - targetBlue) <= tolerance
                val redDominant = r >= 165 && r > g + 45 && r > b + 45 && g < 140 && b < 140
                if (nearTarget || redDominant) {
                    redCount++
                    if (redCount >= 8) {
                        // Ранний выход: в fallback достаточно нескольких уверенных попаданий.
                        return true
                    }
                }
            }
        }

        val ratio = if (sampled > 0) redCount.toFloat() / sampled.toFloat() else 0f
        // Для широкого band нормальный сигнал получается очень "разреженным".
        val fallbackRed = redCount >= 8 && ratio >= 0.003f
        Log.d(
            TAG,
            "Red fallback band: rect=[$left,$top]-[$right,$bottom], hits=$redCount/$sampled (${String.format("%.4f", ratio)}), fallbackRed=$fallbackRed"
        )
        return fallbackRed
    }
}
