package com.egginc.autoclicker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.egginc.autoclicker.MainActivity
import com.egginc.autoclicker.R
import com.egginc.autoclicker.cv.ComputerVision
import com.egginc.autoclicker.utils.ClickerConfig
import com.egginc.autoclicker.utils.ConfigManager
import com.egginc.autoclicker.utils.DisplayUtils
import com.egginc.autoclicker.utils.ScreenMapper

import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Foreground Service с основной логикой автокликера
 */
class AutoclickerService : Service() {
    
    companion object {
        private const val TAG = "AutoclickerService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "autoclicker_channel"
        private const val PAUSE_OWNER_LEGACY = "legacy"
        const val ACTION_STOP = "com.egginc.autoclicker.action.STOP"
        const val ACTION_SET_MODE = "com.egginc.autoclicker.action.SET_MODE"
        const val EXTRA_MODE = "extra_mode"
        
        var isRunning: Boolean
            get() = AutoclickerRuntimeState.isRunning
            private set(value) {
                AutoclickerRuntimeState.isRunning = value
            }
        
        // Текущий режим работы
        var currentMode: ClickerMode
            get() = AutoclickerRuntimeState.currentMode
            set(value) {
                AutoclickerRuntimeState.currentMode = value
            }
        
        // Флаг паузы (когда работает авто-исследования)
        var isPaused: Boolean
            get() = AutoclickerRuntimeState.isPaused
            private set(value) {
                AutoclickerRuntimeState.updatePauseState(value)
            }
        
        // Callback для уведомления о паузе/возобновлении
        var onPauseStateChanged: ((Boolean) -> Unit)?
            get() = AutoclickerRuntimeState.onPauseStateChanged
            set(value) {
                AutoclickerRuntimeState.onPauseStateChanged = value
            }
        
        /**
         * Приостанавливает основные операции (для авто-исследований)
         */
        fun pause(owner: String = PAUSE_OWNER_LEGACY) {
            AutoclickerRuntimeState.acquirePause(owner)
            Log.d(TAG, "Service paused by owner=$owner")
        }
        
        /**
         * Возобновляет основные операции
         */
        fun resume(owner: String = PAUSE_OWNER_LEGACY) {
            AutoclickerRuntimeState.releasePause(owner)
            Log.d(TAG, "Service resume requested by owner=$owner")
        }

        fun isPausedByOthers(owner: String): Boolean {
            return AutoclickerRuntimeState.isPausedByOtherThan(owner)
        }
        
        /**
         * Запускает авто-исследования (вызывается из оверлея)
         */
        fun startResearch(context: Context? = null) {
            ResearchAutomationCoordinator.start(context)
        }
        
        /**
         * Останавливает авто-исследования (вызывается из оверлея)
         */
        fun stopResearch() {
            ResearchAutomationCoordinator.stop()
        }
    }
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var configManager: ConfigManager
    private lateinit var config: ClickerConfig
    private lateinit var computerVision: ComputerVision
    
    // Реальные размеры экрана (получаем из скриншота)
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    
    private var chickenJob: Job? = null
    private var boostJob: Job? = null
    private var giftJob: Job? = null
    private val modeChangeMutex = Mutex()

    private val chickenRunner by lazy {
        ChickenRunner(
            reloadConfig = { loadConfig() },
            configProvider = { config },
            isPausedProvider = { isPaused },
            updateScreenDimensions = { updateScreenDimensions() },
            scaleX = { x -> scaleX(x) },
            scaleY = { y -> scaleY(y) },
            setScreenDimensions = { width, height ->
                screenWidth = width
                screenHeight = height
            },
            screenSizeProvider = { Pair(screenWidth, screenHeight) },
            screenshotsWorkingProvider = { screenshotsWorking },
            statusChickenFarming = { getString(R.string.status_chicken_farming) },
            statusResting = { getString(R.string.status_resting) },
            statusCheckingIndicator = { getString(R.string.status_checking_indicator) },
            statusRedZone = { seconds -> getString(R.string.status_red_zone, seconds) }
        )
    }

    private val giftRunner by lazy {
        GiftRunner(
            reloadConfig = { loadConfig() },
            configProvider = { config },
            updateScreenDimensions = { updateScreenDimensions() },
            scaleX = { x -> scaleX(x) },
            scaleY = { y -> scaleY(y) },
            screenSizeProvider = { Pair(screenWidth, screenHeight) },
            computerVision = computerVision,
            statusFindingGift = { getString(R.string.status_finding_gift) },
            statusCollectingGift = { getString(R.string.status_collecting_gift) }
        )
    }

    private val boostRunner by lazy {
        BoostRunner(
            reloadConfig = { loadConfig() },
            configProvider = { config },
            updateScreenDimensions = { updateScreenDimensions() },
            scaleX = { x -> scaleX(x) },
            scaleY = { y -> scaleY(y) },
            statusCheckingBoosts = { getString(R.string.status_checking_boosts) }
        )
    }
    
    private var lastBoostCheckTime = 0L
    
    override fun onCreate() {
        super.onCreate()
        configManager = ConfigManager(this)
        computerVision = ComputerVision()
        loadConfig()
        // ResearchAutomationService НЕ инициализируется здесь - только через ленивую инициализацию
        Log.d(TAG, "AutoclickerService created")
    }
    
    private fun loadConfig() {
        val baseConfig = configManager.loadConfig()
        config = baseConfig
    }
    
    // Флаг для отслеживания работоспособности скриншотов
    private var screenshotsWorking = true
    private var screenshotFailCount = 0
    private val maxScreenshotFails = 5
    
    /**
     * Обновляет размеры экрана из скриншота
     */
    private suspend fun updateScreenDimensions(): Boolean {
        try {
            if (screenshotsWorking) {
                val screenshot = ClickerAccessibilityService.instance?.takeScreenshotSuspend()
                if (screenshot != null) {
                    // Используем фактический размер скриншота для точного скейлинга кликов.
                    screenWidth = screenshot.width
                    screenHeight = screenshot.height
                    screenshot.recycle()
                    screenshotFailCount = 0
                    Log.d(TAG, "Screen dimensions from screenshot: ${screenWidth}x${screenHeight}")
                    return true
                }

                screenshotFailCount++
                Log.w(TAG, "Screenshot failed (count: $screenshotFailCount)")
                if (screenshotFailCount >= maxScreenshotFails) {
                    screenshotsWorking = false
                    Log.e(TAG, "Screenshots not working! Falling back to WindowManager dimensions")
                    AutoclickerStatusBus.publish(getString(R.string.status_screenshots_unavailable))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get screenshot dimensions", e)
        }

        // Fallback на реальные размеры дисплея, если скриншоты недоступны.
        val dims = DisplayUtils.getRealScreenDimensions(this)
        screenWidth = dims.first
        screenHeight = dims.second
        Log.d(TAG, "Screen dimensions from WindowManager fallback: ${screenWidth}x$screenHeight")
        return false
    }
    
    /**
     * Масштабирует координату X
     */
    private fun scaleX(x: Int): Int {
        if (screenWidth <= 0) return x
        return ScreenMapper.baseToScreenX(x, config.baseResolutionWidth, screenWidth)
    }
    
    /**
     * Масштабирует координату Y
     */
    private fun scaleY(y: Int): Int {
        if (screenHeight <= 0) return y
        return ScreenMapper.baseToScreenY(y, config.baseResolutionHeight, screenHeight)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "Service started with action=$action")

        if (action == ACTION_STOP) {
            Log.d(TAG, "Stop action received")
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_SET_MODE) {
            val modeName = intent.getStringExtra(EXTRA_MODE)
            val newMode = modeName?.let { runCatching { ClickerMode.valueOf(it) }.getOrNull() }
            if (newMode != null) {
                currentMode = newMode
                Log.d(TAG, "Mode updated via action: $newMode")
                if (isRunning) {
                    serviceScope.launch {
                        modeChangeMutex.withLock {
                            restartForModeChange()
                        }
                    }
                }
            } else {
                Log.w(TAG, "Invalid mode in intent: $modeName")
            }
            return START_STICKY
        }
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        if (!isRunning) {
            isRunning = true
            startAutoclicker()
        } else {
            Log.d(TAG, "Autoclicker already running, skip duplicate start")
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        stopAutoclicker()
        isRunning = false
        // Останавливаем исследования (синхронно через companion)
        stopResearch()
        serviceScope.cancel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AutoclickerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.notification_stop), stopIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun startAutoclicker() {
        // Сброс паузы при старте, чтобы избежать залипания после прошлых сессий.
        AutoclickerRuntimeState.clearPauseOwners()
        Log.d(TAG, "Starting autoclicker in mode: $currentMode")
        Log.d(TAG, "Screenshots working: $screenshotsWorking, Smart mode enabled: ${config.smartChickenMode}")
        
        // Запускаем нужные корутины в зависимости от режима
        when (currentMode) {
            ClickerMode.CHICKEN_ONLY -> startChickenFarming()
            ClickerMode.BOOST_ONLY -> startBoostChecking()
            ClickerMode.GIFT_ONLY -> startGiftCollecting()
            ClickerMode.ALL -> {
                startChickenFarming()
                // startBoostChecking() // Отключено - бусты в другом меню
                startGiftCollecting()
            }
        }
        
        // Авто-исследования НЕ запускаем автоматически - только через toggle в оверлее
        // Это исправляет баг когда исследования работали despite выключенном переключателе
    }
    
    private fun stopAutoclicker() {
        Log.d(TAG, "Stopping autoclicker")
        chickenJob?.cancel()
        boostJob?.cancel()
        giftJob?.cancel()
        AutoclickerRuntimeState.clearPauseOwners()
        
        // Останавливаем авто-исследования
        stopResearch()
    }

    private suspend fun restartForModeChange() {
        Log.d(TAG, "Restarting jobs for mode change: $currentMode")
        chickenJob?.cancel()
        boostJob?.cancel()
        giftJob?.cancel()
        chickenJob?.join()
        boostJob?.join()
        giftJob?.join()
        chickenJob = null
        boostJob = null
        giftJob = null
        loadConfig()
        startAutoclicker()
    }
    
    /**
     * Авто-фарм куриц: обычный или умный режим
     */
    private fun startChickenFarming() {
        chickenJob = serviceScope.launch {
            while (isActive) {
                try {
                    // Проверяем паузу (когда работают авто-исследования)
                    if (isPaused) {
                        delay(500)
                        continue
                    }
                    
                    val accessibilityService = ClickerAccessibilityService.instance
                    
                    if (accessibilityService != null) {
                        chickenRunner.runIteration(accessibilityService)
                    } else {
                        delay(1000)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in chicken farming", e)
                    delay(1000)
                }
            }
        }
    }
    
    /**
     * Авто-проверка бустов каждые 30 секунд
     */
    private fun startBoostChecking() {
        boostJob = serviceScope.launch {
            while (isActive) {
                try {
                    loadConfig()
                    val currentTime = System.currentTimeMillis()
                    val intervalMs = config.boostCheckIntervalSec.coerceIn(1, 300) * 1000L
                    
                    if (currentTime - lastBoostCheckTime >= intervalMs) {
                        val service = ClickerAccessibilityService.instance
                        if (service != null) {
                            boostRunner.runIteration(service)
                            lastBoostCheckTime = currentTime
                        }
                    }
                    
                    delay(1000) // Проверяем каждую секунду
                } catch (e: CancellationException) {
                    // Нормальная отмена корутины - не логируем
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in boost checking", e)
                    delay(1000)
                }
            }
        }
    }
    
    /**
     * Авто-сбор подарков - просто тапаем раз в минуту по координатам
     */
    private fun startGiftCollecting() {
        giftJob = serviceScope.launch {
            while (isActive) {
                try {
                    // Проверяем паузу (когда работают авто-исследования)
                    if (isPaused) {
                        delay(500)
                        continue
                    }
                    
                    val service = ClickerAccessibilityService.instance
                    if (service != null) {
                        val intervalMs = giftRunner.runIteration(service)
                        delay(intervalMs.coerceAtLeast(150L))
                    } else {
                        delay(1000)
                    }
                } catch (e: CancellationException) {
                    // Нормальная отмена корутины - не логируем
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in gift collecting", e)
                    delay(1000)
                }
            }
        }
    }
}
