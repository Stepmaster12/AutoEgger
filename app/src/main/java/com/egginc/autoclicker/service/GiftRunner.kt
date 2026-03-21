package com.egginc.autoclicker.service

import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.egginc.autoclicker.cv.ComputerVision
import com.egginc.autoclicker.utils.ClickerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Отвечает только за логику сбора подарков.
 */
class GiftRunner(
    private val reloadConfig: () -> Unit,
    private val configProvider: () -> ClickerConfig,
    private val updateScreenDimensions: suspend () -> Boolean,
    private val scaleX: (Int) -> Int,
    private val scaleY: (Int) -> Int,
    private val screenSizeProvider: () -> Pair<Int, Int>,
    private val computerVision: ComputerVision,
    private val statusFindingGift: () -> String,
    private val statusCollectingGift: () -> String
) {
    companion object {
        private const val TAG = "GiftRunner"
    }

    suspend fun runIteration(service: ClickerAccessibilityService): Long {
        val iterationStartMs = SystemClock.elapsedRealtime()
        reloadConfig()
        val cfg = configProvider()

        AutoclickerStatusBus.publish(statusFindingGift())
        updateScreenDimensions()

        var detectedGiftPoint: Pair<Int, Int>? = null
        if (cfg.useCVForGift && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val giftDetected = checkGiftWithCV(service, cfg)
            if (!giftDetected) {
                Log.d(TAG, "No gift detected via CV, skip this cycle")
                val elapsedMs = SystemClock.elapsedRealtime() - iterationStartMs
                return (cfg.giftCheckIntervalMs - elapsedMs).coerceAtLeast(150L)
            } else {
                detectedGiftPoint = lastDetectedGiftPoint
            }
        }

        AutoclickerStatusBus.publish(statusCollectingGift())

        val fallbackGiftX = scaleX(cfg.giftTapLocation.x)
        val fallbackGiftY = scaleY(cfg.giftTapLocation.y)
        val giftX = detectedGiftPoint?.first ?: fallbackGiftX
        val giftY = detectedGiftPoint?.second ?: fallbackGiftY
        val confirmX = scaleX(cfg.giftConfirmButton.x)
        val confirmY = scaleY(cfg.giftConfirmButton.y)
        val (screenWidth, screenHeight) = screenSizeProvider()

        Log.d(
            TAG,
            "Gift: screen=${screenWidth}x$screenHeight, base=${cfg.baseResolutionWidth}x${cfg.baseResolutionHeight}"
        )
        Log.d(TAG, "Gift click point: ($giftX,$giftY), fallback=($fallbackGiftX,$fallbackGiftY)")

        withContext(Dispatchers.Main) {
            service.performClick(giftX, giftY)
        }

        Log.d(TAG, "Gift box tap at ($giftX, $giftY)")
        delay(1000)

        withContext(Dispatchers.Main) {
            service.performClick(confirmX, confirmY)
        }

        Log.d(TAG, "Gift collect button tap at ($confirmX, $confirmY)")
        delay(500)

        // Если накопилось несколько подарков, пытаемся добрать их сразу,
        // без ожидания следующего большого интервала.
        if (cfg.useCVForGift && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            collectQueuedGiftsFast(service, cfg, confirmX, confirmY)
        }

        val elapsedMs = SystemClock.elapsedRealtime() - iterationStartMs
        return (cfg.giftCheckIntervalMs - elapsedMs).coerceAtLeast(150L)
    }

    private suspend fun checkGiftWithCV(
        service: ClickerAccessibilityService,
        cfg: ClickerConfig
    ): Boolean {
        val first = runCvCheckOnce(service, cfg)
        if (!first.first) {
            lastDetectedGiftPoint = null
            return false
        }
        delay(120)
        val second = runCvCheckOnce(service, cfg)
        if (!second.first) {
            lastDetectedGiftPoint = null
            return false
        }
        val dx = kotlin.math.abs(first.second - second.second)
        val dy = kotlin.math.abs(first.third - second.third)
        val (screenW, screenH) = screenSizeProvider()
        val shortEdge = minOf(screenW, screenH).coerceAtLeast(1)
        val stabilityPx = (shortEdge * cfg.giftCvStabilityRatio).toInt().coerceIn(24, 180)
        val stable = dx <= stabilityPx && dy <= stabilityPx
        if (stable) {
            val avgX = (first.second + second.second) / 2
            val avgY = (first.third + second.third) / 2
            val expectedX = scaleX(cfg.giftTapLocation.x)
            val expectedY = scaleY(cfg.giftTapLocation.y)
            val gateRadius = (shortEdge * 0.30f).toInt().coerceIn(120, 420)
            val gateDx = kotlin.math.abs(avgX - expectedX)
            val gateDy = kotlin.math.abs(avgY - expectedY)
            val insideGate = gateDx <= gateRadius && gateDy <= gateRadius
            if (insideGate) {
                lastDetectedGiftPoint = avgX to avgY
            } else {
                Log.d(
                    TAG,
                    "CV candidate rejected by expected-zone gate: avg=($avgX,$avgY), " +
                        "expected=($expectedX,$expectedY), d=($gateDx,$gateDy), radius=$gateRadius"
                )
                lastDetectedGiftPoint = null
            }
        } else {
            lastDetectedGiftPoint = null
        }
        val accepted = stable && lastDetectedGiftPoint != null
        Log.d(TAG, "CV two-frame confirm: stable=$stable accepted=$accepted, d=($dx,$dy), threshold=$stabilityPx, p1=(${first.second},${first.third}), p2=(${second.second},${second.third})")
        return accepted
    }

    private var lastDetectedGiftPoint: Pair<Int, Int>? = null

    private suspend fun runCvCheckOnce(
        service: ClickerAccessibilityService,
        cfg: ClickerConfig,
        timeoutMs: Long = 3000L
    ): Triple<Boolean, Int, Int> {
        return try {
            val cvResult = CompletableDeferred<Triple<Boolean, Int, Int>>()
            var detectionJob: Job? = null

            val giftX = scaleX(cfg.giftTapLocation.x)
            val giftY = scaleY(cfg.giftTapLocation.y)

            Log.d(TAG, "Starting CV check at ($giftX, $giftY)")

            withContext(Dispatchers.Main) {
                detectionJob = service.findGiftWithCV(computerVision, cfg, giftX, giftY, cfg.cvDetectionMode) { found, foundX, foundY ->
                    if (!cvResult.isCompleted) {
                        cvResult.complete(Triple(found, foundX, foundY))
                    }
                }
            }

            val result = withTimeoutOrNull(timeoutMs) {
                cvResult.await()
            }

            if (result == null) {
                detectionJob?.cancel()
                Log.d(TAG, "CV gift detection timeout")
                Triple(false, 0, 0)
            } else {
                detectionJob?.cancel()
                val (found, x, y) = result
                Log.d(TAG, "CV gift detection final result: $found at ($x, $y)")
                Triple(found, x, y)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in CV gift check", e)
            Triple(false, 0, 0)
        }
    }

    private suspend fun collectQueuedGiftsFast(
        service: ClickerAccessibilityService,
        cfg: ClickerConfig,
        confirmX: Int,
        confirmY: Int
    ) {
        var missesInRow = 0
        repeat(5) { attempt ->
            if (missesInRow >= 2) {
                Log.d(TAG, "Quick extra gift checks stopped after consecutive misses")
                return
            }

            delay(250)
            val probe = runCvCheckOnce(service, cfg, timeoutMs = 1200L)
            if (!probe.first) {
                missesInRow++
                Log.d(TAG, "Quick extra gift check #${attempt + 1}: miss ($missesInRow in row)")
                return@repeat
            }

            missesInRow = 0
            val (found, x, y) = probe
            if (!found) return@repeat

            Log.d(TAG, "Quick extra gift collect #${attempt + 1} at ($x,$y)")
            withContext(Dispatchers.Main) {
                service.performClick(x, y)
            }
            delay(450)
            withContext(Dispatchers.Main) {
                service.performClick(confirmX, confirmY)
            }
            delay(350)
        }
    }
}
