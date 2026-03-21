package com.egginc.autoclicker.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.core.graphics.get
import com.egginc.autoclicker.utils.ClickerConfig
import com.egginc.autoclicker.utils.ConfigManager
import com.egginc.autoclicker.utils.DisplayUtils
import com.egginc.autoclicker.utils.ScreenMapper
import kotlinx.coroutines.*

/**
 * Сервис для автоматизации покупки исследований в лаборатории
 * Использует цветовую детекцию для поиска зелёных кнопок "RESEARCH"
 */
class ResearchAutomationService(
    private val accessibilityService: ClickerAccessibilityService,
    private val configManager: ConfigManager,
    private val autoclickerController: AutoclickerController = DefaultAutoclickerController
) {
    companion object {
        private const val TAG = "ResearchAutomation"
        private const val PAUSE_OWNER_RESEARCH = "auto_research"
        private val VALID_BUTTON_SCAN_OFFSETS = arrayOf(
            intArrayOf(0, 0),
            intArrayOf(-2, 0), intArrayOf(2, 0), intArrayOf(0, -2), intArrayOf(0, 2),
            intArrayOf(-4, 0), intArrayOf(4, 0), intArrayOf(0, -4), intArrayOf(0, 4),
            intArrayOf(-2, -2), intArrayOf(2, -2), intArrayOf(-2, 2), intArrayOf(2, 2),
            intArrayOf(-6, 0), intArrayOf(6, 0), intArrayOf(0, -6), intArrayOf(0, 6)
        )
    }

    private data class AdaptiveSwipePlan(
        val swipesUp: Int,
        val swipesDown: Int,
        val upStartOffsetBase: Int,
        val upEndOffsetBase: Int,
        val downStartOffsetBase: Int,
        val downEndOffsetBase: Int
    )

    private data class ResearchSpeedProfile(
        val modeName: String,
        val interBarrageSettleMs: Long,
        val emptyRetryDelayMs: Long,
        val postButtonPauseMs: Long,
        val clickCadenceMs: Long
    )

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var researchJob: Job? = null
    private var isRunning = false
    
    // Кэш текущего разрешения экрана
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var templateGreenButton: Bitmap? = null
    private var templateGreyButton: Bitmap? = null
    private var templateBought: Bitmap? = null
    private var templateGreenScaled: Bitmap? = null
    private var templateGreyScaled: Bitmap? = null
    private var templateBoughtScaled: Bitmap? = null
    @Volatile
    private var templatesReleased = false
    
    /**
     * Получает текущее разрешение экрана через скриншот
     * Важно: metrics даёт размер без панели навигации, а скриншот - полный экран
     */
    private suspend fun updateScreenDimensionsFromScreenshot() {
        try {
            // Делаем тестовый скриншот чтобы получить реальное разрешение
            val testBitmap = accessibilityService.takeScreenshotSuspend()
            if (testBitmap != null) {
                screenWidth = testBitmap.width
                screenHeight = testBitmap.height
                testBitmap.recycle()
                Log.d(TAG, "Screen dimensions from screenshot: ${screenWidth}x${screenHeight}")
            } else {
                // Fallback на реальные размеры дисплея если скриншот не удался
                val dims = DisplayUtils.getRealScreenDimensions(accessibilityService)
                screenWidth = dims.first
                screenHeight = dims.second
                Log.d(TAG, "Screen dimensions from DisplayUtils (fallback): ${screenWidth}x${screenHeight}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get screen dimensions from screenshot, using WindowManager", e)
            getScreenDimensionsFromWindowManager()
        }
    }
    
    /**
     * Получает реальное разрешение экрана через WindowManager
     */
    private fun getScreenDimensionsFromWindowManager() {
        try {
            val dims = DisplayUtils.getRealScreenDimensions(accessibilityService)
            screenWidth = dims.first
            screenHeight = dims.second
            Log.d(TAG, "Screen dimensions from WindowManager: ${screenWidth}x${screenHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get screen dimensions from WindowManager", e)
            // Последний fallback - используем дефолтные значения только если всё остальное не сработало
            if (screenWidth == 0) screenWidth = 1440
            if (screenHeight == 0) screenHeight = 3120
        }
    }
    
    /**
     * @deprecated используйте updateScreenDimensionsFromScreenshot
     */
    private fun updateScreenDimensions() {
        // Получаем реальное разрешение через WindowManager
        getScreenDimensionsFromWindowManager()
    }
    
    /**
     * Масштабирует координату X под текущее разрешение
     */
    private fun scaleX(x: Int, config: ClickerConfig): Int {
        if (screenWidth == 0) updateScreenDimensions()
        return ScreenMapper.baseToScreenX(x, config.baseResolutionWidth, screenWidth)
    }
    
    /**
     * Масштабирует координату Y под текущее разрешение
     */
    private fun scaleY(y: Int, config: ClickerConfig): Int {
        if (screenHeight == 0) updateScreenDimensions()
        return ScreenMapper.baseToScreenY(y, config.baseResolutionHeight, screenHeight)
    }

    fun start() {
        if (isRunning) {
            Log.d(TAG, "Research already running, ignoring start()")
            return
        }
        
        // Сбрасываем флаг остановки перед стартом
        stopRequested = false
        isRunning = true

        researchJob = coroutineScope.launch {
            // Сначала ждём полный интервал перед первым запуском
            val initialConfig = configManager.loadConfig()
            val initialDelayMs = initialConfig.autoResearchIntervalSec * 1000L
            Log.d(TAG, "Starting research automation, initial delay: ${initialDelayMs}ms")
            
            // Используем delayWithStopCheck для возможности ранней остановки
            if (!delayWithStopCheck(initialDelayMs)) {
                Log.d(TAG, "Research stopped during initial delay")
                return@launch
            }
            
            while (isActive && !stopRequested) {
                try {
                    if (shouldStop()) {
                        Log.d(TAG, "Stop requested, exiting research loop")
                        break
                    }
                    
                    val config = configManager.loadConfig()
                    if (config.autoResearchEnabled) {
                        waitUntilNoExternalPause()
                        if (shouldStop()) break
                        checkAndBuyResearch(config)
                    }
                    
                    if (shouldStop()) break
                    
                    // После работы ждём полный интервал
                    val intervalMs = config.autoResearchIntervalSec * 1000L
                    Log.d(TAG, "Waiting ${intervalMs}ms until next check")
                    if (!delayWithStopCheck(intervalMs)) {
                        Log.d(TAG, "Research stopped during interval wait")
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in research automation", e)
                    if (shouldStop()) break
                    delay(5000)
                }
            }
            
            Log.d(TAG, "Research automation loop ended")
            isRunning = false
        }
    }

    @Volatile
    private var stopRequested = false
    
    fun stop() {
        Log.d(TAG, "Stopping research automation, job active: ${researchJob?.isActive}")
        stopRequested = true
        isRunning = false
        researchJob?.cancel()
        researchJob = null
        Log.d(TAG, "Research automation stopped and job cleared")
    }
    
    /**
     * Проверяет нужно ли остановиться
     */
    private fun shouldStop(): Boolean {
        return stopRequested || !isRunning || researchJob?.isActive != true
    }
    
    fun isRunning(): Boolean = isRunning

    /**
     * Проверяет и покупает доступные исследования
     * Скроллит вверх и вниз чтобы найти все доступные кнопки
     */
    private suspend fun checkAndBuyResearch(config: ClickerConfig) {
        Log.d(TAG, "Checking for available research...")
        val speed = getResearchSpeedProfile(config)
        Log.d(TAG, "Research speed mode: ${speed.modeName}")
        waitUntilNoExternalPause()
        if (shouldStop()) return

        // Проверяем, доступен ли Accessibility Service
        if (!ClickerAccessibilityService.isRunning()) {
            Log.w(TAG, "Accessibility service not running")
            return
        }
        
        if (shouldStop()) {
            Log.d(TAG, "Stop requested before starting research check")
            return
        }

        // Останавливаем другие скрипты перед покупкой исследований
        pauseOtherScripts()

        try {
            // 1. Открываем лабораторию
            openResearchMenu(config)
            if (!delayWithStopCheck(2500)) return // Ждём открытия + анимацию блюра

            // 2. Сначала скроллим ВВЕРХ к началу списка (используем настройку swipesUp)
            val swipePlan = buildAdaptiveSwipePlan(config)
            Log.d(
                TAG,
                "Adaptive swipes: up=${swipePlan.swipesUp}, down=${swipePlan.swipesDown}, " +
                    "up(${swipePlan.upStartOffsetBase}->${swipePlan.upEndOffsetBase}), " +
                    "down(${swipePlan.downStartOffsetBase}->${swipePlan.downEndOffsetBase})"
            )
            scrollToTop(config, swipePlan)
            if (!delayWithStopCheck(1000)) return

            // 3. Проходим список сверху вниз - ищем и покупаем
            val swipesDown = swipePlan.swipesDown
            
            // Масштабируем смещения
            val offsetX = scaleX(200, config)
            val scanLeft = scaleX(config.researchScanArea.left, config)
            val scanTop = scaleY(config.researchScanArea.top, config)
            val offsetYDownStart = scaleY(swipePlan.downStartOffsetBase, config)
            val offsetYDownEnd = scaleY(swipePlan.downEndOffsetBase, config)
            
            // Сначала ищем на текущем экране (верх списка)
            findAndBuyButtons(config, speed)
            if (shouldStop()) return
            
            // Делаем скроллы вниз с паузами 1.8 сек
            repeat(swipesDown) { scrollNum ->
                if (shouldStop()) return@repeat
                
                Log.d(TAG, "Scroll down #${scrollNum + 1}/$swipesDown")
                
                // Скроллим вниз (снизу вверх = контент двигается вниз)
                withContext(Dispatchers.Main) {
                    accessibilityService.performSwipe(
                        scanLeft + offsetX,
                        scanTop + offsetYDownStart,  // Начинаем снизу
                        scanLeft + offsetX,
                        scanTop + offsetYDownEnd,    // Заканчиваем сверху
                        400
                    )
                }
                if (!delayWithStopCheck(1800)) return // 1.8 сек - полная остановка скролла
                
                // Ищем и покупаем на новом экране
                findAndBuyButtons(config, speed)
            }

            if (shouldStop()) return
            
            Log.d(TAG, "Finished processing research list")
            if (!delayWithStopCheck(1000)) return // Пауза перед закрытием

            // 4. Закрываем лабораторию
            closeResearchMenu(config)
            if (!delayWithStopCheck(1500)) return // Ждём закрытия + исчезновение блюра
        } finally {
            // Возобновляем другие скрипты независимо от результата
            resumeOtherScripts()
        }
    }
    
    /**
     * Delay с проверкой на остановку - возвращает true если продолжаем, false если остановлены
     */
    private suspend fun delayWithStopCheck(timeMs: Long): Boolean {
        val checkInterval = 100L // Проверяем каждые 100ms
        var elapsed = 0L
        
        while (elapsed < timeMs) {
            if (shouldStop()) {
                Log.d(TAG, "Stop detected during delay at ${elapsed}ms/${timeMs}ms")
                return false
            }
            if (autoclickerController.isPausedByOthers(PAUSE_OWNER_RESEARCH)) {
                delay(80)
                continue
            }
            val remaining = timeMs - elapsed
            val delayTime = minOf(checkInterval, remaining)
            delay(delayTime)
            elapsed += delayTime
        }
        return !shouldStop()
    }

    private suspend fun waitUntilNoExternalPause() {
        while (!shouldStop() && autoclickerController.isPausedByOthers(PAUSE_OWNER_RESEARCH)) {
            delay(120)
        }
    }
    
    /**
     * Приостанавливает другие скрипты (курики, подарки) на время покупки исследований
     */
    private fun pauseOtherScripts() {
        Log.d(TAG, "Pausing other scripts for research")
        autoclickerController.pause(PAUSE_OWNER_RESEARCH)
    }
    
    /**
     * Возобновляет другие скрипты после покупки исследований
     */
    private fun resumeOtherScripts() {
        Log.d(TAG, "Resuming other scripts after research")
        autoclickerController.resume(PAUSE_OWNER_RESEARCH)
    }
    
    /**
     * Ищет и покупает зелёные кнопки на текущем экране с барражами
     * Сначала покупает самую верхнюю, потом проверяет снова
     * Останавливается когда на экране не остаётся зелёных кнопок
     */
    private suspend fun findAndBuyButtons(config: ClickerConfig) {
        findAndBuyButtons(config, getResearchSpeedProfile(config))
    }

    private suspend fun findAndBuyButtons(config: ClickerConfig, speed: ResearchSpeedProfile) {
        val maxBarrages = 3 // Максимум 3 барража = 30 кликов
        val maxIterations = 20 // Защита от бесконечного цикла
        var iterations = 0
        var emptyStreak = 0
        
        while (iterations < maxIterations) {
            if (shouldStop()) return
            iterations++
            
            // Ищем зелёные кнопки
            val buttons = findGreenResearchButtons(config)
            
            // Если нет кнопок - выходим (переходим к свайпу)
            if (buttons.isEmpty()) {
                emptyStreak++
                Log.d(TAG, "No green buttons found on screen (empty streak=$emptyStreak)")
                // На маленьких экранах и при анимациях бывают ложные "пустые" кадры.
                // Требуем 2 подряд пустых скана перед переходом к свайпу.
                if (emptyStreak >= 2) break
                if (!delayWithStopCheck(speed.emptyRetryDelayMs)) return
                continue
            }
            emptyStreak = 0
            
            // Берём самую верхнюю кнопку (с минимальным Y)
            val topButton = buttons.minByOrNull { it.y } ?: break
            
            Log.d(TAG, "Processing top button at (${topButton.x}, ${topButton.y})")
            
            // Барраж 1: всегда 10 кликов
            repeat(10) {
                if (shouldStop()) return@repeat
                buyResearch(topButton)
                delay(speed.clickCadenceMs)
            }
            
            if (shouldStop()) return
            
            // Проверяем нужен ли второй/третий барраж
            for (barrageNum in 2..maxBarrages) {
                if (shouldStop()) return
                
                if (!delayWithStopCheck(speed.interBarrageSettleMs)) return // Короткая стабилизация UI между барражами
                
                // Быстрый локальный пробник вместо полного перескана всей зоны.
                val stillGreen = isButtonStillGreenQuick(config, topButton)
                
                // Ищем ту же кнопку (по близости координат)
                if (!stillGreen) {
                    Log.d(TAG, "Button gone/gray after ${barrageNum-1} barrages, moving to next")
                    break // Кнопка пропала - выходим к поиску следующей
                }
                
                // Проверяем - не закончились ли деньги (кнопка есть но цвет не зелёный)
                // На самом деле findGreenResearchButtons вернёт только зелёные,
                // так что если кнопка есть в списке - значит зелёная
                
                // Делаем ещё 10 кликов
                Log.d(TAG, "Barrage #$barrageNum for button at (${topButton.x}, ${topButton.y})")
                repeat(10) {
                    if (shouldStop()) return@repeat
                    buyResearch(topButton)
                    delay(speed.clickCadenceMs)
                }
            }
            
            if (!delayWithStopCheck(speed.postButtonPauseMs)) return // Пауза перед поиском следующей кнопки
        }
        
        if (iterations >= maxIterations) {
            Log.w(TAG, "Max iterations reached, stopping to prevent infinite loop")
        }
    }

    private fun getResearchSpeedProfile(config: ClickerConfig): ResearchSpeedProfile {
        return if (config.researchFastMode) {
            ResearchSpeedProfile(
                modeName = "FAST",
                interBarrageSettleMs = 170L,
                emptyRetryDelayMs = 120L,
                postButtonPauseMs = 80L,
                clickCadenceMs = 35L
            )
        } else {
            ResearchSpeedProfile(
                modeName = "NORMAL",
                interBarrageSettleMs = 320L,
                emptyRetryDelayMs = 280L,
                postButtonPauseMs = 220L,
                clickCadenceMs = 50L
            )
        }
    }

    /**
     * Быстро проверяет, осталась ли конкретная кнопка зелёной, по локальной области вокруг неё.
     * Это намного быстрее полного сканирования всей research-зоны.
     */
    private suspend fun isButtonStillGreenQuick(config: ClickerConfig, button: ResearchButton): Boolean {
        val bitmap = accessibilityService.takeScreenshotSuspend() ?: return false
        try {
            screenWidth = bitmap.width
            screenHeight = bitmap.height
            val tolerance = config.colorTolerance
            val shortEdge = minOf(screenWidth, screenHeight)
            val radius = when {
                shortEdge <= 800 -> 20
                shortEdge <= 1100 -> 24
                else -> 28
            }
            val step = if (shortEdge <= 900) 2 else 3

            val left = (button.x - radius).coerceIn(0, bitmap.width - 1)
            val right = (button.x + radius).coerceIn(left, bitmap.width - 1)
            val top = (button.y - radius).coerceIn(0, bitmap.height - 1)
            val bottom = (button.y + radius).coerceIn(top, bitmap.height - 1)

            var greenHits = 0
            var sampled = 0
            for (y in top..bottom step step) {
                for (x in left..right step step) {
                    sampled++
                    if (isResearchGreenButton(bitmap[x, y], tolerance)) {
                        greenHits++
                        if (greenHits >= 4) return true
                    }
                }
            }
            val ratio = if (sampled > 0) greenHits.toFloat() / sampled else 0f
            return greenHits >= 3 && ratio >= 0.06f
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Открывает меню лаборатории
     */
    private suspend fun openResearchMenu(config: ClickerConfig) {
        // Получаем реальное разрешение из скриншота
        updateScreenDimensionsFromScreenshot()
        
        val x = scaleX(config.researchMenuButton.x, config)
        val y = scaleY(config.researchMenuButton.y, config)
        Log.d(TAG, "Opening research menu at ($x, $y) [original: ${config.researchMenuButton.x}, ${config.researchMenuButton.y}]")
        Log.d(TAG, "Screen: ${screenWidth}x$screenHeight, Base: ${config.baseResolutionWidth}x${config.baseResolutionHeight}")
        
        withContext(Dispatchers.Main) {
            accessibilityService.performClick(x, y)
        }
        delay(500) // Ждём открытия + анимацию блюра
    }

    /**
     * Закрывает меню лаборатории
     */
    private suspend fun closeResearchMenu(config: ClickerConfig) {
        val x = scaleX(config.researchCloseButton.x, config)
        val y = scaleY(config.researchCloseButton.y, config)
        Log.d(TAG, "Closing research menu at ($x, $y)")
        withContext(Dispatchers.Main) {
            accessibilityService.performClick(x, y)
        }
    }
    
    /**
     * Скроллит вверх к началу списка (использует настройку researchSwipesUp)
     * Чтобы скроллить вверх (к началу), тянем сверху вниз (палец идет вниз)
     */
    private suspend fun scrollToTop(config: ClickerConfig, swipePlan: AdaptiveSwipePlan) {
        val offsetX = scaleX(200, config)
        val scanLeft = scaleX(config.researchScanArea.left, config)
        val scanTop = scaleY(config.researchScanArea.top, config)
        val startY = scaleY(swipePlan.upStartOffsetBase, config)  // Начинаем сверху
        val endY = scaleY(swipePlan.upEndOffsetBase, config)      // Заканчиваем снизу
        
        val swipesUp = swipePlan.swipesUp
        
        // Скроллим вверх (сверху вниз = контент двигается вверх к началу)
        repeat(swipesUp) { i ->
            if (shouldStop()) return@repeat
            
            Log.d(TAG, "Scroll up #${i + 1}/$swipesUp")
            withContext(Dispatchers.Main) {
                accessibilityService.performSwipe(
                    scanLeft + offsetX,
                    scanTop + startY,  // Начинаем сверху
                    scanLeft + offsetX,
                    scanTop + endY,    // Заканчиваем снизу (палец идет вниз)
                    300
                )
            }
            if (!delayWithStopCheck(500)) return
        }
    }

    /**
     * Адаптирует количество и длину свайпов под фактический экран.
     * Большой/вытянутый экран: свайпов меньше и свайп длиннее.
     * Маленький/короткий экран: свайпов больше и свайп короче.
     */
    private fun buildAdaptiveSwipePlan(config: ClickerConfig): AdaptiveSwipePlan {
        val baseAspect = config.baseResolutionHeight.toFloat() / config.baseResolutionWidth.toFloat()
        val currentAspect = if (screenWidth > 0) screenHeight.toFloat() / screenWidth.toFloat() else baseAspect
        val baseHeight = config.baseResolutionHeight.toFloat()
        val currentHeight = if (screenHeight > 0) screenHeight.toFloat() else baseHeight
        val shortEdge = minOf(screenWidth.takeIf { it > 0 } ?: config.baseResolutionWidth, screenHeight.takeIf { it > 0 } ?: config.baseResolutionHeight)

        // < 1.0 -> экран крупнее/выше базового (меньше свайпов), > 1.0 -> меньше экран (больше свайпов).
        val aspectFactor = (baseAspect / currentAspect).coerceIn(0.75f, 1.35f)
        val heightFactor = (baseHeight / currentHeight).coerceIn(0.75f, 1.35f)
        val countFactor = (aspectFactor * 0.7f + heightFactor * 0.3f).coerceIn(0.7f, 1.4f)

        val swipesUp = (config.researchSwipesUp * countFactor).toInt().coerceIn(5, 50)
        val swipesDown = (config.researchSwipesDown * countFactor).toInt().coerceIn(5, 40)

        // Длина свайпа меняется в обратную сторону: меньше свайпов -> длиннее свайп.
        // На маленьких экранах дополнительно укорачиваем свайп, чтобы не перескакивать кнопки.
        val smallScreenFactor = when {
            shortEdge <= 760 -> 0.74f
            shortEdge <= 900 -> 0.82f
            shortEdge <= 1080 -> 0.90f
            else -> 1.0f
        }
        val lengthFactor = ((1f / countFactor) * smallScreenFactor).coerceIn(0.60f, 1.2f)

        // Базовые оффсеты в координатах base-resolution.
        val upCenter = 500
        val upBaseLength = 600
        val upLength = (upBaseLength * lengthFactor).toInt()
        val upStart = (upCenter - upLength / 2).coerceIn(80, 1100)
        val upEnd = (upCenter + upLength / 2).coerceIn(160, 1300)

        val downCenter = 450
        val downBaseLength = 700
        val downLength = (downBaseLength * lengthFactor).toInt()
        val downStart = (downCenter + downLength / 2).coerceIn(220, 1200)
        val downEnd = (downCenter - downLength / 2).coerceIn(60, 900)

        return AdaptiveSwipePlan(
            swipesUp = swipesUp,
            swipesDown = swipesDown,
            upStartOffsetBase = upStart,
            upEndOffsetBase = upEnd,
            downStartOffsetBase = downStart,
            downEndOffsetBase = downEnd
        )
    }

    /**
     * Нажимает кнопку покупки исследования
     */
    private suspend fun buyResearch(button: ResearchButton) {
        Log.d(TAG, "Buying research at (${button.x}, ${button.y})")
        withContext(Dispatchers.Main) {
            accessibilityService.performClick(button.x, button.y)
        }
    }

    /**
     * Ищет зелёные кнопки RESEARCH на экране
     */
    private suspend fun findGreenResearchButtons(config: ClickerConfig): List<ResearchButton> {
        val buttons = mutableListOf<ResearchButton>()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "Research detection requires Android 11+")
            return buttons
        }

        // Делаем скриншот
        val bitmap = accessibilityService.takeScreenshotSuspend() ?: return buttons

        try {
            // Обновляем размеры из скриншота (это реальное полное разрешение экрана)
            screenWidth = bitmap.width
            screenHeight = bitmap.height
            Log.d(TAG, "Screenshot dimensions: ${screenWidth}x${screenHeight}")
            
            val scanArea = config.researchScanArea

            // Масштабируем координаты под текущий экран
            val left = ScreenMapper.baseToScreenX(scanArea.left, config.baseResolutionWidth, screenWidth)
            val top = ScreenMapper.baseToScreenY(scanArea.top, config.baseResolutionHeight, screenHeight)
            val right = ScreenMapper.baseToScreenX(scanArea.right, config.baseResolutionWidth, screenWidth).coerceAtLeast(left + 1)
            val bottom = ScreenMapper.baseToScreenY(scanArea.bottom, config.baseResolutionHeight, screenHeight).coerceAtLeast(top + 1)

            // Ищем зелёные кнопки в области сканирования
            // Используем точный цвет RGB(25, 172, 0) с погрешностью ±20
            val shortEdge = minOf(screenWidth, screenHeight)
            val step = when {
                shortEdge <= 800 -> 6
                shortEdge <= 1100 -> 8
                else -> 10
            } // На маленьких экранах сканируем плотнее, чтобы не пропускать одиночные кнопки.
            val tolerance = config.colorTolerance // Погрешность (по умолчанию 20)
            var lastY = -1
            val minButtonWidthPx = (screenWidth * config.researchMinButtonWidthRatio).toInt().coerceIn(40, 220)
            val minButtonHeightPx = (screenHeight * config.researchMinButtonHeightRatio).toInt().coerceIn(16, 120)
            val dedupYPx = (screenHeight * config.researchButtonDedupYRatio).toInt().coerceIn(20, 220)
            ensureResearchTemplatesLoaded()

            for (y in top until bottom step step) {
                for (x in left until right step step) {
                    if (x >= bitmap.width || y >= bitmap.height) continue

                    val pixel = bitmap[x, y]
                    if (isResearchGreenButton(pixel, tolerance)) {
                        // Нашли зелёный пиксель, проверяем соседей с шагом 2
                        if (isValidResearchButton(bitmap, x, y, tolerance)) {
                            // Центрируем кнопку и проверяем её размер
                            val (centerX, centerY, width, height) = findButtonDimensions(bitmap, x, y, tolerance)
                            
                            // Фильтруем по минимальному размеру:
                            // Кнопка RESEARCH: ~200x60 на 1440p, ~100x30 на 720p
                            // Галочка в квадрате: ~80x80 на 1440p, ~40x40 на 720p, но сама галочка ~20x20
                            // Порог 70x25 работает для всех экранов
                            if (width < minButtonWidthPx || height < minButtonHeightPx) {
                                Log.d(TAG, "Skipping small green area (${width}x${height}) at ($centerX, $centerY) - likely a checkmark")
                                continue
                            }

                            if (!isLikelyResearchBuyButton(bitmap, centerX, centerY, config)) {
                                continue
                            }
                            
                            // Проверяем, не слишком ли близко к предыдущей кнопке
                            if (lastY == -1 || kotlin.math.abs(centerY - lastY) > dedupYPx) {
                                buttons.add(ResearchButton(centerX, centerY))
                                lastY = centerY
                                Log.d(TAG, "Found research button at ($centerX, $centerY), size: ${width}x${height}")
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "Total buttons found: ${buttons.size}")

        } finally {
            bitmap.recycle()
        }

        return buttons
    }

    /**
     * Проверяет, является ли цвет зелёной кнопкой RESEARCH
     * Использует точный цвет RGB(25, 172, 0) с погрешностью ±20
     */
    private fun isResearchGreenButton(pixel: Int, tolerance: Int = 20): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        // 1) Точный цвет кнопки: RGB(25,172,0) ± tolerance
        val exact = kotlin.math.abs(r - 25) <= tolerance &&
            kotlin.math.abs(g - 172) <= tolerance &&
            kotlin.math.abs(b - 0) <= tolerance

        // 2) Fallback для вариаций рендера/масштаба на маленьких экранах:
        // ярко-зелёный, доминирующий G-канал.
        val vividGreen = g >= 110 &&
            g > r + 30 &&
            g > b + 30 &&
            b <= 120

        return exact || vividGreen
    }
    
    /**
     * Проверяет, является ли область валидной кнопкой RESEARCH
     * Использует сканирование с шагом 2 пикселя от центра
     */
    private fun isValidResearchButton(bitmap: Bitmap, x: Int, y: Int, tolerance: Int = 20): Boolean {
        var greenCount = 0
        var totalChecked = 0

        for (offset in VALID_BUTTON_SCAN_OFFSETS) {
            val px = x + offset[0]
            val py = y + offset[1]

            if (px >= 0 && px < bitmap.width && py >= 0 && py < bitmap.height) {
                totalChecked++
                if (isResearchGreenButton(bitmap[px, py], tolerance)) {
                    greenCount++
                }
            }
        }

        // Если более 50% пикселей совпадают - это кнопка
        return greenCount > totalChecked * 0.5
    }

    /**
     * Находит центр и размеры кнопки
     * Возвращает: centerX, centerY, width, height
     */
    private fun findButtonDimensions(bitmap: Bitmap, startX: Int, startY: Int, tolerance: Int = 20): ButtonDimensions {
        var minX = startX
        var maxX = startX
        var minY = startY
        var maxY = startY

        // Идём влево
        for (x in startX downTo 0) {
            if (isResearchGreenButton(bitmap[x, startY], tolerance)) {
                minX = x
            } else break
        }

        // Идём вправо
        for (x in startX until bitmap.width) {
            if (isResearchGreenButton(bitmap[x, startY], tolerance)) {
                maxX = x
            } else break
        }

        val centerX = (minX + maxX) / 2

        // Идём вверх от центра
        for (y in startY downTo 0) {
            if (centerX < bitmap.width && y < bitmap.height &&
                isResearchGreenButton(bitmap[centerX, y], tolerance)) {
                minY = y
            } else break
        }

        // Идём вниз от центра
        for (y in startY until bitmap.height) {
            if (centerX < bitmap.width && y < bitmap.height &&
                isResearchGreenButton(bitmap[centerX, y], tolerance)) {
                maxY = y
            } else break
        }

        val width = maxX - minX + 1
        val height = maxY - minY + 1
        val centerY = (minY + maxY) / 2

        return ButtonDimensions(centerX, centerY, width, height)
    }

    data class ButtonDimensions(val centerX: Int, val centerY: Int, val width: Int, val height: Int)

    data class ResearchButton(val x: Int, val y: Int)

    private fun ensureResearchTemplatesLoaded() {
        if (templateGreenButton == null || templateGreenButton?.isRecycled == true) {
            templateGreenButton = loadTemplateBitmap("green_button", "green-button")
            templateGreenScaled?.recycle()
            templateGreenScaled = null
        }
        if (templateGreyButton == null || templateGreyButton?.isRecycled == true) {
            templateGreyButton = loadTemplateBitmap("grey_button", "gray_button", "grey-button")
            templateGreyScaled?.recycle()
            templateGreyScaled = null
        }
        if (templateBought == null || templateBought?.isRecycled == true) {
            templateBought = loadTemplateBitmap("bought")
            templateBoughtScaled?.recycle()
            templateBoughtScaled = null
        }

        // Предмасштабированные шаблоны для compareTemplate (горячий путь)
        if (templateGreenScaled == null || templateGreenScaled?.isRecycled == true) {
            templateGreenButton?.let { templateGreenScaled = Bitmap.createScaledBitmap(it, 36, 14, true) }
        }
        if (templateGreyScaled == null || templateGreyScaled?.isRecycled == true) {
            templateGreyButton?.let { templateGreyScaled = Bitmap.createScaledBitmap(it, 36, 14, true) }
        }
        if (templateBoughtScaled == null || templateBoughtScaled?.isRecycled == true) {
            templateBought?.let { templateBoughtScaled = Bitmap.createScaledBitmap(it, 36, 14, true) }
        }
    }

    private fun loadTemplateBitmap(vararg names: String): Bitmap? {
        for (name in names) {
            val resId = accessibilityService.resources.getIdentifier(name, "drawable", accessibilityService.packageName)
            if (resId != 0) {
                try {
                    val bmp = BitmapFactory.decodeResource(accessibilityService.resources, resId)
                    if (bmp != null) {
                        Log.d(TAG, "Loaded research template: $name")
                        return bmp
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to decode template: $name", e)
                }
            }
        }
        return null
    }

    private fun isLikelyResearchBuyButton(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        config: ClickerConfig
    ): Boolean {
        val green = templateGreenButton ?: return true
        val grey = templateGreyButton
        val bought = templateBought

        val patchW = (screenWidth * 0.14f).toInt().coerceIn(90, 240)
        val patchH = (screenHeight * 0.02f).toInt().coerceIn(26, 70)
        val scaledPatch = createScaledResearchPatch(bitmap, centerX, centerY, patchW, patchH)
        val greenScore: Float
        val greyScore: Float
        val boughtScore: Float
        try {
            val greenTemplate = templateGreenScaled ?: Bitmap.createScaledBitmap(green, 36, 14, true)
            val greyTemplate = grey?.let { templateGreyScaled ?: Bitmap.createScaledBitmap(it, 36, 14, true) }
            val boughtTemplate = bought?.let { templateBoughtScaled ?: Bitmap.createScaledBitmap(it, 36, 14, true) }
            greenScore = compareScaledPatchToTemplate(scaledPatch, greenTemplate)
            greyScore = greyTemplate?.let { compareScaledPatchToTemplate(scaledPatch, it) } ?: 0f
            boughtScore = boughtTemplate?.let { compareScaledPatchToTemplate(scaledPatch, it) } ?: 0f

            if (greenTemplate !== templateGreenScaled) greenTemplate.recycle()
            if (greyTemplate != null && greyTemplate !== templateGreyScaled) greyTemplate.recycle()
            if (boughtTemplate != null && boughtTemplate !== templateBoughtScaled) boughtTemplate.recycle()
        } finally {
            scaledPatch.recycle()
        }

        val strict = config.researchTemplateStrictFilter
        val minGreen = if (strict) 0.56f else 0.50f
        val deltaGrey = if (strict) 0.08f else 0.04f
        val deltaBought = if (strict) 0.09f else 0.05f

        val accepted = greenScore > minGreen &&
            greenScore > greyScore + deltaGrey &&
            greenScore > boughtScore + deltaBought

        if (!accepted) {
            Log.d(
                TAG,
                "Template rejected at ($centerX,$centerY): green=${"%.2f".format(greenScore)}, " +
                    "grey=${"%.2f".format(greyScore)}, bought=${"%.2f".format(boughtScore)}"
            )
        }

        return accepted
    }

    private fun compareTemplate(
        screenshot: Bitmap,
        template: Bitmap,
        centerX: Int,
        centerY: Int,
        patchW: Int,
        patchH: Int
    ): Float {
        val left = (centerX - patchW / 2).coerceIn(0, screenshot.width - 1)
        val top = (centerY - patchH / 2).coerceIn(0, screenshot.height - 1)
        val right = (left + patchW).coerceIn(left + 1, screenshot.width)
        val bottom = (top + patchH).coerceIn(top + 1, screenshot.height)

        val patch = Bitmap.createBitmap(screenshot, left, top, right - left, bottom - top)
        val scaledPatch = Bitmap.createScaledBitmap(patch, 36, 14, true)
        val scaledTemplate = when (template) {
            templateGreenButton -> templateGreenScaled ?: Bitmap.createScaledBitmap(template, 36, 14, true)
            templateGreyButton -> templateGreyScaled ?: Bitmap.createScaledBitmap(template, 36, 14, true)
            templateBought -> templateBoughtScaled ?: Bitmap.createScaledBitmap(template, 36, 14, true)
            else -> Bitmap.createScaledBitmap(template, 36, 14, true)
        }
        patch.recycle()

        var diff = 0f
        val total = 36 * 14
        for (y in 0 until 14) {
            for (x in 0 until 36) {
                val p = scaledPatch[x, y]
                val t = scaledTemplate[x, y]
                val dr = kotlin.math.abs(Color.red(p) - Color.red(t))
                val dg = kotlin.math.abs(Color.green(p) - Color.green(t))
                val db = kotlin.math.abs(Color.blue(p) - Color.blue(t))
                diff += (dr + dg + db) / 3f
            }
        }
        scaledPatch.recycle()
        // Кэшированные scaled template не освобождаем здесь.
        if (scaledTemplate !== templateGreenScaled &&
            scaledTemplate !== templateGreyScaled &&
            scaledTemplate !== templateBoughtScaled
        ) {
            scaledTemplate.recycle()
        }

        val meanDiff = diff / total
        return (1f - (meanDiff / 255f)).coerceIn(0f, 1f)
    }

    private fun createScaledResearchPatch(
        screenshot: Bitmap,
        centerX: Int,
        centerY: Int,
        patchW: Int,
        patchH: Int
    ): Bitmap {
        val left = (centerX - patchW / 2).coerceIn(0, screenshot.width - 1)
        val top = (centerY - patchH / 2).coerceIn(0, screenshot.height - 1)
        val right = (left + patchW).coerceIn(left + 1, screenshot.width)
        val bottom = (top + patchH).coerceIn(top + 1, screenshot.height)

        val patch = Bitmap.createBitmap(screenshot, left, top, right - left, bottom - top)
        return Bitmap.createScaledBitmap(patch, 36, 14, true).also { patch.recycle() }
    }

    private fun compareScaledPatchToTemplate(scaledPatch: Bitmap, scaledTemplate: Bitmap): Float {
        var diff = 0f
        val total = 36 * 14
        for (y in 0 until 14) {
            for (x in 0 until 36) {
                val p = scaledPatch[x, y]
                val t = scaledTemplate[x, y]
                val dr = kotlin.math.abs(Color.red(p) - Color.red(t))
                val dg = kotlin.math.abs(Color.green(p) - Color.green(t))
                val db = kotlin.math.abs(Color.blue(p) - Color.blue(t))
                diff += (dr + dg + db) / 3f
            }
        }
        val meanDiff = diff / total
        return (1f - (meanDiff / 255f)).coerceIn(0f, 1f)
    }
    
    /**
     * Очищает ресурсы сервиса. Должен вызываться при уничтожении.
     */
    fun destroy() {
        Log.d(TAG, "Destroy called, cancelling all jobs")
        stopRequested = true
        isRunning = false
        val activeJob = researchJob
        researchJob = null
        if (activeJob != null) {
            activeJob.invokeOnCompletion {
                releaseTemplateBitmaps()
                coroutineScope.cancel()
                Log.d(TAG, "ResearchAutomationService destroyed (post-job)")
            }
            activeJob.cancel()
        } else {
            releaseTemplateBitmaps()
            coroutineScope.cancel()
            Log.d(TAG, "ResearchAutomationService destroyed")
        }
    }

    private fun releaseTemplateBitmaps() {
        synchronized(this) {
            if (templatesReleased) return
            templatesReleased = true
            templateGreenButton?.recycle()
            templateGreyButton?.recycle()
            templateBought?.recycle()
            templateGreenScaled?.recycle()
            templateGreyScaled?.recycle()
            templateBoughtScaled?.recycle()
            templateGreenButton = null
            templateGreyButton = null
            templateBought = null
            templateGreenScaled = null
            templateGreyScaled = null
            templateBoughtScaled = null
        }
    }
}
