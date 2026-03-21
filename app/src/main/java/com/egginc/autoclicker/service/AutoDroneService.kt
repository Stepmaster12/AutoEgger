package com.egginc.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.egginc.autoclicker.R
import com.egginc.autoclicker.utils.ClickerConfig
import com.egginc.autoclicker.utils.ConfigManager
import com.egginc.autoclicker.utils.DisplayUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Сервис для автоматической ловли дронов через свайпы
 * 
 * ПРИНЦИП РАБОТЫ:
 * 1. Заходит в кастомизацию базы (фиксирует экран, отдаляет вид)
 * 2. Выполняет быстрые свайпы по диагонали игровой области
 * 3. Дроны ловятся при пересечении траектории пальцем
 * 
 * При активации:
 * - Блокируются другие функции (курицы, подарки, бусты)
 * - Оверлей можно свернуть/развернуть между свайпами
 */
class AutoDroneService(
    private val accessibilityService: AccessibilityService,
    private val configManager: ConfigManager
) {
    companion object {
        private const val TAG = "AutoDroneService"
        
        // Пауза между циклами свайпов (мс)
        const val CYCLE_PAUSE_MS = 150L
        
        // Длительность каждого свайпа (мс)
        const val SWIPE_DURATION_MS = 250L
        
        // Задержки для входа в кастомизацию (мс)
        const val ANIMATION_DELAY_MS = 800L
        const val TRANSITION_DELAY_MS = 1000L
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var droneJob: Job? = null
    @Volatile
    private var isRunning = false
    private var isInCustomization = false
    
    // Callback для уведомления UI о состоянии
    var onDroneStateChanged: ((Boolean) -> Unit)? = null

    fun start() {
        if (isRunning) {
            Log.d(TAG, "Already running, ignoring start request")
            return
        }
        
        isRunning = true
        onDroneStateChanged?.invoke(true)
        
        droneJob = coroutineScope.launch {
            try {
                Log.d(TAG, "Starting drone catching cycle")
                
                // Вход в кастомизацию базы
                enterCustomization()
                
                // Начинаем цикл свайпов
                while (isActive && isRunning) {
                    performSwipeCycle()
                    delay(CYCLE_PAUSE_MS)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Drone service cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error in auto drone service", e)
            } finally {
                // Выход из кастомизации при остановке
                exitCustomization()
                isRunning = false
                isInCustomization = false
                onDroneStateChanged?.invoke(false)
            }
        }
    }

    fun stop() {
        Log.d(TAG, "Stopping drone service")
        isRunning = false
        droneJob?.cancel()
        droneJob = null
    }

    fun isRunning(): Boolean = isRunning

    /**
     * Вход в кастомизацию базы
     */
    private suspend fun enterCustomization() {
        Log.d(TAG, "Entering customization mode...")
        
        val config = configManager.loadConfig()
        
        // Получаем реальные размеры экрана через WindowManager
        val (screenWidth, screenHeight) = getRealScreenDimensions()
        
        // Масштабируем координаты
        val menuX = scaleX(config.customizeMenuButton.x, screenWidth, config)
        val menuY = scaleY(config.customizeMenuButton.y, screenHeight, config)
        val click1X = scaleX(config.customizeFirstClick.x, screenWidth, config)
        val click1Y = scaleY(config.customizeFirstClick.y, screenHeight, config)
        val click2X = scaleX(config.customizeSecondClick.x, screenWidth, config)
        val click2Y = scaleY(config.customizeSecondClick.y, screenHeight, config)
        
        // Шаг 1: Открываем меню
        dispatchClick(menuX, menuY)
        delay(ANIMATION_DELAY_MS)
        
        // Шаг 2: Первый клик в меню
        dispatchClick(click1X, click1Y)
        delay(TRANSITION_DELAY_MS)
        
        // Шаг 3: Входим в кастомизацию
        dispatchClick(click2X, click2Y)
        delay(ANIMATION_DELAY_MS)
        
        isInCustomization = true
        Log.d(TAG, "Customization mode entered")
        showToast(accessibilityService.getString(R.string.toast_drone_mode_active))
    }
    
    /**
     * Масштабирует координату X под текущее разрешение
     */
    private fun scaleX(x: Int, screenWidth: Int, config: ClickerConfig): Int {
        return (x * screenWidth.toFloat() / config.baseResolutionWidth).toInt()
    }
    
    /**
     * Масштабирует координату Y под текущее разрешение
     */
    private fun scaleY(y: Int, screenHeight: Int, config: ClickerConfig): Int {
        return (y * screenHeight.toFloat() / config.baseResolutionHeight).toInt()
    }
    
    /**
     * Получает реальное разрешение экрана через WindowManager
     */
    private fun getRealScreenDimensions(): Pair<Int, Int> {
        return DisplayUtils.getRealScreenDimensions(accessibilityService)
    }

    /**
     * Выход из кастомизации
     */
    private suspend fun exitCustomization() {
        if (!isInCustomization) return
        
        Log.d(TAG, "Exiting customization mode...")
        
        withContext(Dispatchers.Main) {
            try {
                accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            } catch (e: Exception) {
                Log.e(TAG, "Error exiting customization", e)
            }
        }
        
        delay(300)
        
        withContext(Dispatchers.Main) {
            try {
                accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            } catch (e: Exception) {
                Log.e(TAG, "Error closing menu", e)
            }
        }
        
        isInCustomization = false
        Log.d(TAG, "Customization mode exited")
    }

    /**
     * Выполняет один цикл свайпов по диагонали:
     * От края до края и обратно, циклически
     */
    private suspend fun performSwipeCycle() {
        val config = configManager.loadConfig()
        
        // Получаем реальные размеры экрана через WindowManager
        val (screenWidth, screenHeight) = getRealScreenDimensions()
        
        // Масштабируем координаты свайпа
        val startX = scaleX(config.droneSwipeStart.x, screenWidth, config)
        val startY = scaleY(config.droneSwipeStart.y, screenHeight, config)
        val endX = scaleX(config.droneSwipeEnd.x, screenWidth, config)
        val endY = scaleY(config.droneSwipeEnd.y, screenHeight, config)
        
        Log.d(TAG, "Swipe cycle: ($startX,$startY) <-> ($endX,$endY) [base: ${config.droneSwipeStart.x},${config.droneSwipeStart.y} <-> ${config.droneSwipeEnd.x},${config.droneSwipeEnd.y}]")
        
        // 1. От начала к концу
        if (!isRunning) return
        if (!dispatchSwipe(startX, startY, endX, endY)) return
        delay(35)
        
        // 2. Обратно от конца к началу
        if (!isRunning) return
        if (!dispatchSwipe(endX, endY, startX, startY)) return
        delay(35)
        
        // 3. Ещё раз от начала к концу (для надёжности)
        if (!isRunning) return
        if (!dispatchSwipe(startX, startY, endX, endY)) return
        delay(35)
        
        // 4. И обратно
        if (!isRunning) return
        if (!dispatchSwipe(endX, endY, startX, startY)) return
        delay(35)
    }

    /**
     * Выполняет свайп через AccessibilityService
     */
    private suspend fun dispatchSwipe(startX: Int, startY: Int, endX: Int, endY: Int): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                val path = Path().apply {
                    moveTo(startX.toFloat(), startY.toFloat())
                    lineTo(endX.toFloat(), endY.toFloat())
                }

                val stroke = GestureDescription.StrokeDescription(path, 0, SWIPE_DURATION_MS)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                val service = accessibilityService as? ClickerAccessibilityService
                if (service == null) {
                    if (continuation.isActive) continuation.resume(false)
                    return@suspendCancellableCoroutine
                }

                val callback = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                }

                val dispatched = service.dispatchGesture(gesture, callback, Handler(Looper.getMainLooper()))
                if (!dispatched && continuation.isActive) {
                    continuation.resume(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error dispatching swipe", e)
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    /**
     * Выполняет клик по координатам
     */
    private suspend fun dispatchClick(x: Int, y: Int) {
        withContext(Dispatchers.Main) {
            try {
                val path = Path().apply {
                    moveTo(x.toFloat(), y.toFloat())
                }
                
                val stroke = GestureDescription.StrokeDescription(
                    path,
                    0,
                    50
                )
                
                val gesture = GestureDescription.Builder()
                    .addStroke(stroke)
                    .build()
                
                val result = (accessibilityService as? ClickerAccessibilityService)
                    ?.dispatchGesture(gesture, null, null) ?: false
                Log.d(TAG, "Click at ($x, $y) result: $result")
            } catch (e: Exception) {
                Log.e(TAG, "Error dispatching click", e)
            }
        }
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
        Log.d(TAG, "AutoDroneService destroyed")
    }
}
