package com.egginc.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.egginc.autoclicker.R
import com.egginc.autoclicker.utils.DisplayUtils
import kotlinx.coroutines.*

/**
 * Сервис для автоматической активации буста 2x деньги
 * 
 * ТРЕБУЕТСЯ отключить рекламу в настройках игры:
 * Настройки > Приватность и данные > Реклама
 * 
 * Работает по фиксированным координатам:
 * - Открывает меню бустов
 * - Кликает на кнопку "FREE" для Video Doubler (1220, 710)
 * - Закрывает меню (1350, 440)
 * 
 * При первой активации: запуск через 30 секунд
 * Повторы: каждые 55 минут (настраивается от 1 до 60 минут)
 */
class AutoBoostService(
    private val accessibilityService: AccessibilityService
) {
    companion object {
        private const val TAG = "AutoBoostService"
        
        // Фиксированные координаты для нажатия (на базе 1440x3120)
        const val BOOST_BUTTON_X = 1220
        const val BOOST_BUTTON_Y = 710
        const val CLOSE_BUTTON_X = 1350
        const val CLOSE_BUTTON_Y = 440
        
        // Задержки
        const val INITIAL_DELAY_MS = 30_000L      // 30 секунд перед первой активацией
        const val DEFAULT_REPEAT_MINUTES = 55     // По умолчанию 55 минут
        const val MIN_REPEAT_MINUTES = 1
        const val MAX_REPEAT_MINUTES = 60
        
        // Координаты кнопки открытия меню бустов (иконка ракеты)
        const val MENU_BUTTON_X = 335
        const val MENU_BUTTON_Y = 2605
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var boostJob: Job? = null
    @Volatile
    private var isRunning = false
    
    // Callback для уведомления UI о состоянии
    var onBoostStateChanged: ((Boolean) -> Unit)? = null
    
    // Callback для приостановки/возобновления других функций
    var onPauseOtherFeatures: ((Boolean) -> Unit)? = null

    /**
     * Запускает цикл авто-буста
     * Автоматически активирует буст каждые N минут
     * @param repeatMinutes Интервал повторения в минутах (1-60)
     */
    fun start(repeatMinutes: Int = DEFAULT_REPEAT_MINUTES) {
        if (isRunning) {
            Log.d(TAG, "Already running, ignoring start request")
            return
        }
        
        isRunning = true
        onBoostStateChanged?.invoke(true)

        val intervalMs = repeatMinutes.coerceIn(MIN_REPEAT_MINUTES, MAX_REPEAT_MINUTES) * 60_000L
        
        boostJob = coroutineScope.launch {
            try {
                Log.d(TAG, "Starting auto boost. Initial delay: ${INITIAL_DELAY_MS}ms, repeat interval: ${intervalMs}ms (${repeatMinutes} min)")
                showToast(accessibilityService.getString(R.string.auto_boost_started))
                
                // Первая активация через 30 секунд
                delay(INITIAL_DELAY_MS)
                
                while (isActive && isRunning) {
                    activateBoost2x()
                    
                    if (isActive && isRunning) {
                        Log.d(TAG, "Waiting ${intervalMs}ms until next activation")
                        delay(intervalMs)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Auto boost cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error in auto boost", e)
                showToast(accessibilityService.getString(R.string.auto_boost_error_generic, e.message))
            } finally {
                isRunning = false
                onBoostStateChanged?.invoke(false)
                onPauseOtherFeatures?.invoke(false)
            }
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping auto boost service")
        isRunning = false
        boostJob?.cancel()
        boostJob = null
    }

    fun isRunning(): Boolean = isRunning

    /**
     * Выполняет одну итерацию авто-буста
     * Открывает меню бустов, активирует буст, закрывает меню бустов
     */
    private suspend fun activateBoost2x() {
        Log.d(TAG, "=== Activating 2x boost ===")
        showToast(accessibilityService.getString(R.string.activating_boost))
        
        // Приостанавливаем другие функции
        onPauseOtherFeatures?.invoke(true)
        delay(500)
        
        try {
            // Делает скриншот и проверяет, открыто ли меню бустов
            val screenshot = takeScreenshot()
            val (screenWidth, screenHeight) = if (screenshot != null) {
                val w = screenshot.width
                val h = screenshot.height
                screenshot.recycle()
                Pair(w, h)
            } else {
                // Получаем реальное разрешение через WindowManager
                getRealScreenDimensions()
            }
            
            // Масштабируем координаты под текущий экран
            val scaleX = screenWidth / 1440f
            val scaleY = screenHeight / 3120f
            
            val menuX = (MENU_BUTTON_X * scaleX).toInt()
            val menuY = (MENU_BUTTON_Y * scaleY).toInt()
            val boostX = (BOOST_BUTTON_X * scaleX).toInt()
            val boostY = (BOOST_BUTTON_Y * scaleY).toInt()
            val closeX = (CLOSE_BUTTON_X * scaleX).toInt()
            val closeY = (CLOSE_BUTTON_Y * scaleY).toInt()
            
            Log.d(TAG, "Screen: ${screenWidth}x${screenHeight}, scale: ${scaleX}x${scaleY}")
            Log.d(TAG, "Menu click: ($menuX, $menuY)")
            Log.d(TAG, "Boost click: ($boostX, $boostY)")
            Log.d(TAG, "Close click: ($closeX, $closeY)")
            
            // 1. Открывает меню бустов
            withContext(Dispatchers.Main) {
                performClick(menuX, menuY)
            }
            delay(2000) // Ждём открытия + анимацию
            
            // 2. Активирует буст (кликаем на кнопку FREE)
            withContext(Dispatchers.Main) {
                performClick(boostX, boostY)
            }
            delay(1000) // Ждём активации
            
            // 3. Закрывает меню бустов
            withContext(Dispatchers.Main) {
                performClick(closeX, closeY)
            }
            delay(500)
            
            showToast(accessibilityService.getString(R.string.toast_boost_activated))
            Log.d(TAG, "=== 2x boost activated successfully ===")
            
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error activating boost", e)
            showToast(accessibilityService.getString(R.string.activation_error))
        } finally {
            // Возобновляем другие функции
            onPauseOtherFeatures?.invoke(false)
        }
    }

    private fun performClick(x: Int, y: Int) {
        (accessibilityService as? ClickerAccessibilityService)?.performClick(x, y)
    }

    private suspend fun takeScreenshot(): Bitmap? {
        return (accessibilityService as? ClickerAccessibilityService)?.takeScreenshotSuspend()
    }
    
    /**
     * Получает реальное разрешение экрана через WindowManager
     */
    private fun getRealScreenDimensions(): Pair<Int, Int> {
        return DisplayUtils.getRealScreenDimensions(accessibilityService)
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(accessibilityService, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Очищает ресурсы сервиса
     */
    fun destroy() {
        stop()
        coroutineScope.cancel()
        Log.d(TAG, "AutoBoostService destroyed")
    }
}
