package com.egginc.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.os.Build
import android.os.SystemClock

import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.egginc.autoclicker.cv.ComputerVision
import com.egginc.autoclicker.R
import com.egginc.autoclicker.utils.ClickerConfig
import java.io.File

/**
 * Accessibility Service для симуляции кликов и жестов
 */
class ClickerAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "ClickerAccessibility"
        
        // Синглтон для доступа к сервису из других классов
        @Volatile
        var instance: ClickerAccessibilityService? = null
            private set
        
        fun isRunning(): Boolean = instance != null
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var giftTemplateBitmap: Bitmap? = null
    private val screencapMutex = Mutex()
    private val screenshotMutex = Mutex()
    @Volatile
    private var lastScreenshotRequestMs: Long = 0L
    @Volatile
    private var screenshotFailureStreak: Int = 0
    @Volatile
    private var screenshotBackoffUntilMs: Long = 0L
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "Service created")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        giftTemplateBitmap?.recycle()
        giftTemplateBitmap = null
        instance = null
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
        
        // Скриншоты доступны через takeScreenshot() (Android 11+)
        Log.d(TAG, "Screenshot capability available: ${Build.VERSION.SDK_INT >= Build.VERSION_CODES.R}")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Не используем, но метод обязательный
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
    
    /**
     * Выполняет одиночный тап по указанным координатам
     */
    fun performClick(x: Int, y: Int, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        
        val result = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)

        if (!result) {
            Log.w(TAG, "dispatchGesture click failed at ($x, $y)")
            callback?.invoke(false)
        }
    }
    
    /**
     * Выполняет долгое нажатие (long press)
     */
    @Suppress("unused")
    fun performLongPress(x: Int, y: Int, durationMs: Long, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        
        val result = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
        
        if (!result) {
            callback?.invoke(false)
        }
    }
    
    /**
     * Выполняет медленный свайп - эмулирует удержание кнопки
     * Свайп длится несколько секунд, что позволяет "удерживать" кнопку
     * 
     * @param startX Начальная X
     * @param startY Начальная Y  
     * @param endX Конечная X (чуть сдвинуть для имитации свайпа)
     * @param endY Конечная Y
     * @param durationMs Длительность свайпа (3-5 секунд)
     */
    fun performSlowSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        
        val result = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
        
        if (!result) {
            Log.w(TAG, "dispatchGesture slow swipe failed: ($startX,$startY)->($endX,$endY), duration=$durationMs")
            callback?.invoke(false)
        }
    }
    
    /**
     * Выполняет свайп
     */
    fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 300) {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        
        dispatchGesture(gesture, null, null)
    }
    
    /**
     * Находит подарок на экране используя OpenCV
     * @param computerVision ComputerVision для детекции подарка
     * @param callback вызывается с результатом (found, x, y) или (false, 0, 0)
     */
    fun findGiftWithCV(
        computerVision: ComputerVision,
        config: ClickerConfig,
        centerX: Int,
        centerY: Int,
        cvMode: String,
        callback: (Boolean, Int, Int) -> Unit
    ): Job {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "CV detection requires Android 11+")
            callback(false, 0, 0)
            return CompletableDeferred<Unit>().apply { complete(Unit) }
        }

        // Используем suspend версию с fallback на screencap
        return serviceScope.launch(Dispatchers.Main) {
            val bitmap = takeScreenshotSuspend()
            
            if (bitmap == null) {
                Log.e(TAG, "Failed to take screenshot for CV")
                callback(false, 0, 0)
                return@launch
            }
            
            Log.d(TAG, "Screenshot taken for CV: ${bitmap.width}x${bitmap.height}")
            
            // Поиск подарка в отдельном потоке
            withContext(Dispatchers.Default) {
                try {
                    val template = getGiftTemplateBitmap()
                    val detected = computerVision.detectGiftBox(bitmap, config, centerX, centerY, template, cvMode)
                    
                    withContext(Dispatchers.Main) {
                        if (detected.found) {
                            Log.d(TAG, "Gift found at (${detected.x}, ${detected.y}), score=${detected.score}")
                        } else {
                            Log.d(TAG, "Gift not found on screen")
                        }
                        callback(detected.found, detected.x, detected.y)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "CV detection failed", e)
                    withContext(Dispatchers.Main) {
                        callback(false, 0, 0)
                    }
                } finally {
                    // Гарантированно освобождаем память в любом случае
                    bitmap.recycle()
                }
            }
        }
    }

    private fun getGiftTemplateBitmap(): Bitmap? {
        if (giftTemplateBitmap != null && !giftTemplateBitmap!!.isRecycled) {
            return giftTemplateBitmap
        }
        return try {
            BitmapFactory.decodeResource(resources, R.drawable.gift_box)?.also {
                giftTemplateBitmap = it
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load gift template", e)
            null
        }
    }
    
    /**
     * Берет скриншот и возвращает Bitmap (suspend-версия)
     * @return Bitmap или null если ошибка (пользователь должен вызвать recycle!)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun takeScreenshotSuspend(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "Screenshot requires Android 11+")
            return null
        }

        return screenshotMutex.withLock {
            val lockNow = SystemClock.elapsedRealtime()
            if (lockNow < screenshotBackoffUntilMs) {
                return@withLock null
            }

            // Системный API не любит очень частые запросы скриншота.
            // Держим минимальный интервал между вызовами.
            val now = SystemClock.elapsedRealtime()
            val minScreenshotIntervalMs = 520L
            val sinceLast = now - lastScreenshotRequestMs
            if (sinceLast in 0 until minScreenshotIntervalMs) {
                delay(minScreenshotIntervalMs - sinceLast)
            }
            lastScreenshotRequestMs = SystemClock.elapsedRealtime()

            // На Samsung Android 16 бывают проблемы с частыми скриншотами
            // Ждём немного перед попыткой
            delay(25)
            
            // Пробуем стандартный метод AccessibilityService с таймаутом
            val maxAttempts = when {
                screenshotFailureStreak >= 6 -> 1
                screenshotFailureStreak >= 3 -> 2
                else -> 3
            }
            repeat(maxAttempts) { attempt ->
                val result = try {
                    withTimeout(1000) {  // 1 секунда таймаут
                        takeScreenshotOnce()
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "Screenshot timeout!")
                    null
                }
                if (result != null) {
                    screenshotFailureStreak = 0
                    screenshotBackoffUntilMs = 0L
                    return@withLock result
                }
                if (attempt == maxAttempts - 1) {
                    Log.w(TAG, "Accessibility screenshot failed after retries")
                }
                // Увеличиваем задержку между попытками
                delay(200 + attempt * 100L)
            }
            
            Log.e(TAG, "All Accessibility screenshot attempts failed, trying screencap fallback...")
            
            // Fallback на screencap
            val screencapResult = takeScreenshotViaScreencap()
            if (screencapResult != null) {
                screenshotFailureStreak = 0
                screenshotBackoffUntilMs = 0L
                return@withLock screencapResult
            }
            
            Log.e(TAG, "All screenshot methods failed")
            screenshotFailureStreak = (screenshotFailureStreak + 1).coerceAtMost(10)
            val backoff = when {
                screenshotFailureStreak >= 8 -> 5000L
                screenshotFailureStreak >= 5 -> 3000L
                screenshotFailureStreak >= 3 -> 1500L
                else -> 700L
            }
            screenshotBackoffUntilMs = SystemClock.elapsedRealtime() + backoff
            return@withLock null
        }
    }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeScreenshotOnce(): Bitmap? = suspendCancellableCoroutine { continuation ->
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                        if (hardwareBitmap == null) {
                            Log.e(TAG, "Failed to wrap hardware buffer")
                            continuation.resume(null) {}
                            return
                        }

                        // Конвертируем HARDWARE bitmap в SOFTWARE
                        val bitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        hardwareBitmap.recycle()

                        continuation.resume(bitmap) { bitmap.recycle() }
                    } finally {
                        // Важно: закрываем native buffer всегда, иначе возможна утечка памяти.
                        screenshot.hardwareBuffer.close()
                    }
                }
                
                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Screenshot failed with error code: $errorCode")
                    continuation.resume(null) {}
                }
            })
    }
    
    /**
     * Fallback метод для скриншота через screencap команду
     * Работает без специальных разрешений на Android 16+
     */
    private suspend fun takeScreenshotViaScreencap(): Bitmap? = screencapMutex.withLock {
        withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                // Используем уникальное имя, чтобы параллельные задачи не портили файл друг друга.
                val unique = "${System.currentTimeMillis()}_${Thread.currentThread().id}"
                tempFile = File(getExternalFilesDir(null), "screenshot_temp_$unique.png")
                val path = tempFile.absolutePath
                
                Log.d(TAG, "Taking screenshot via screencap to: $path")
                
                val process = Runtime.getRuntime().exec("screencap -p $path")
                val exitCode = process.waitFor()
                
                if (exitCode != 0) {
                    Log.e(TAG, "screencap failed with exit code: $exitCode")
                    val error = process.errorStream.bufferedReader().readText()
                    Log.e(TAG, "screencap error: $error")
                    return@withContext null
                }
                
                if (!tempFile.exists()) {
                    Log.e(TAG, "Screenshot file not created")
                    return@withContext null
                }
                
                val bitmap = BitmapFactory.decodeFile(path)
                
                if (bitmap != null) {
                    Log.d(TAG, "Screenshot via screencap successful: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.e(TAG, "Failed to decode screenshot from file")
                }
                
                bitmap
            } catch (e: Exception) {
                Log.e(TAG, "Error taking screenshot via screencap", e)
                null
            } finally {
                tempFile?.delete()
            }
        }
    }
}
