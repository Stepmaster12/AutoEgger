package com.egginc.autoclicker.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.egginc.autoclicker.MainActivity
import com.egginc.autoclicker.R
import com.egginc.autoclicker.utils.ConfigManager
import com.egginc.autoclicker.utils.LocaleHelper
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.system.exitProcess

/**
 * Сервис для отображения плавающего окна (оверлея)
 */
class OverlayService : Service() {
    override fun attachBaseContext(newBase: Context?) {
        val context = newBase?.let { LocaleHelper.setLocale(it) }
        super.attachBaseContext(context)
    }
    
    companion object {
        private const val TAG = "OverlayService"
        private const val PAUSE_OWNER_AUTO_BOOST = "auto_boost"
        
        @Volatile
        var isRunning = false
            private set
        
        private var currentStatus = ""
        private var statusViewRef: WeakReference<TextView>? = null
        private var indicatorViewRef: WeakReference<View>? = null
        
        // Статические переменные для сохранения состояний toggles при пересоздании оверлея
        @Volatile
        var researchToggleState = false
        @Volatile
        var boostToggleState = false
        @Volatile
        var droneToggleState = false
        
        /**
         * Обновляет статус в оверлее
         */
        fun updateStatus(status: String) {
            currentStatus = status
            Handler(Looper.getMainLooper()).post {
                statusViewRef?.get()?.text = status
            }
        }
    }
    
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null
    
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMinimized = false
    
    private var currentMode = ClickerMode.ALL
    private val autoclickerController: AutoclickerController = DefaultAutoclickerController
    
    // Переключатели авто-фич
    private var toggleResearch: ToggleButton? = null
    private var toggleBoost2x: ToggleButton? = null
    private var toggleDrones: ToggleButton? = null
    
    // Coroutine scope для операций
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    private lateinit var configManager: ConfigManager
    private var autoBoostService: AutoBoostService? = null
    private var autoDroneService: AutoDroneService? = null
    private var boundAccessibilityService: ClickerAccessibilityService? = null
    
    // Состояние режима дронов (блокирует другие функции)
    private var droneModeActive = false
    private val resizeHandler = Handler(Looper.getMainLooper())
    private var lastScreenWidthPx = -1
    private var lastScreenHeightPx = -1
    private var lastDensityDpi = -1
    private var restartInProgress = false
    @Volatile
    private var overlayWatcherActive = false
    private val overlayResizeWatcher = object : Runnable {
        override fun run() {
            var nextDelayMs = 1000L
            try {
                if (!overlayWatcherActive) return
                if (!hasActiveAutomationWork()) {
                    nextDelayMs = 4500L
                    return
                }
                val currentWidth = getCurrentScreenWidthPx()
                val currentHeight = getCurrentScreenHeightPx()
                val currentDensity = getCurrentDensityDpi()
                if (currentWidth > 0 && currentHeight > 0 &&
                    (currentWidth != lastScreenWidthPx ||
                        currentHeight != lastScreenHeightPx ||
                        (currentDensity > 0 && currentDensity != lastDensityDpi))
                ) {
                    val previousWidth = lastScreenWidthPx
                    val previousHeight = lastScreenHeightPx
                    val previousDensity = lastDensityDpi
                    lastScreenWidthPx = currentWidth
                    lastScreenHeightPx = currentHeight
                    if (currentDensity > 0) {
                        lastDensityDpi = currentDensity
                    }
                    if (!restartInProgress && previousWidth > 0 && previousHeight > 0) {
                        forceRestartAppOnResolutionChange(
                            previousWidth,
                            previousHeight,
                            currentWidth,
                            currentHeight,
                            previousDensity,
                            currentDensity
                        )
                    } else {
                        updateOverlayContentWidth()
                        overlayView?.let { view ->
                            params?.let { windowManager.updateViewLayout(view, it) }
                        }
                    }
                }
            } finally {
                if (overlayWatcherActive) {
                    resizeHandler.postDelayed(this, nextDelayMs)
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "OverlayService onCreate")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        configManager = ConfigManager(this)
        
        isRunning = true
        createNotificationChannel()
        
        // Слушаем изменения состояния паузы
        autoclickerController.setPauseStateListener { isPaused ->
            if (isPaused) {
                updateStatus(getString(R.string.status_paused_research))
            } else {
                updateStatus(if (autoclickerController.isRunning()) getString(R.string.status_running) else getString(R.string.status_waiting))
            }
        }
        AutoclickerStatusBus.setListener(::updateStatus)
        
        Log.d(TAG, "OverlayService created successfully")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "OverlayService onStartCommand")
        
        // Защита от спама - если оверлей уже создан, не создаём дубль
        if (overlayView != null) {
            Log.d(TAG, "Overlay already exists, ignoring duplicate start")
            return START_STICKY
        }
        
        // Запускаем как foreground service
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1002, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1002, createNotification())
            }
            Log.d(TAG, "Foreground service started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
        }
        
        Log.d(TAG, "Creating overlay view...")
        createOverlay()
        
        Log.d(TAG, "OverlayService started successfully")
        return START_STICKY
    }
    
    /**
     * Создает канал уведомлений
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "overlay_channel",
                getString(R.string.channel_overlay_name),
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_overlay_description)
            }
            
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): android.app.Notification {
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        return androidx.core.app.NotificationCompat.Builder(this, "overlay_channel")
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_active))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        // Полная остановка всех функций при закрытии оверлея
        stopAutoclicker()
        coroutineScope.cancel()
        // Очищаем сервисы
        autoBoostService?.destroy()
        autoBoostService = null
        autoDroneService?.destroy()
        autoDroneService = null
        boundAccessibilityService = null
        autoclickerController.setPauseStateListener(null)
        AutoclickerStatusBus.setListener(null)
        stopOverlayResizeWatcher()
        removeOverlay()
        isRunning = false
        Log.d(TAG, "OverlayService destroyed - all functions stopped")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Если пользователь меняет разрешение/дисплей во время работы,
        // пересчитываем ширину оверлея и обновляем layout.
        updateOverlayContentWidth()
        overlayView?.let { view ->
            params?.let { windowManager.updateViewLayout(view, it) }
        }
    }
    
    /**
     * Создает оверлей
     */
    @SuppressLint("InflateParams")
    private fun createOverlay() {
        Log.d(TAG, "Creating overlay...")
        // Применяем текущую локаль через applicationContext
        val contextWithLocale = LocaleHelper.setLocale(applicationContext)
        val inflater = LayoutInflater.from(contextWithLocale)
        overlayView = inflater.inflate(R.layout.overlay_layout, null)
        Log.d(TAG, "Overlay view inflated")
        
        // Параметры окна
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 100
        }
        
        setupViews()
        updateOverlayContentWidth()
        setupDrag()
        
        windowManager.addView(overlayView, params)
    }

    /**
     * Адаптирует ширину оверлея под текущее разрешение экрана.
     * Это защищает от "огромного" оверлея при смене системного разрешения.
     */
    private fun updateOverlayContentWidth() {
        val content = overlayView?.findViewById<LinearLayout>(R.id.overlay_content) ?: return
        val screenWidth = getCurrentScreenWidthPx().coerceAtLeast(1)
        val screenHeight = getCurrentScreenHeightPx().coerceAtLeast(1)
        val shortEdge = minOf(screenWidth, screenHeight)
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val shortEdgeDp = shortEdge / density

        // Универсальная модель:
        // - считаем ширину от короткой стороны в dp (физический размер),
        // - ограничиваем диапазон в dp и в процентах от короткой стороны.
        val targetDp = (shortEdgeDp * 0.50f).coerceIn(145f, 240f)
        val targetPx = (targetDp * density).toInt()
        val minByRatio = (shortEdge * 0.42f).toInt()
        val maxByRatio = (shortEdge * 0.68f).toInt()
        val maxByPadding = shortEdge - (16 * density).toInt()
        val finalWidth = targetPx.coerceIn(minByRatio, minOf(maxByRatio, maxByPadding))

        val lp = content.layoutParams
        lp.width = finalWidth
        content.layoutParams = lp
        Log.d(
            TAG,
            "Overlay width: screen=${screenWidth}x${screenHeight}, short=${shortEdge}, dp=${"%.1f".format(shortEdgeDp)}, final=$finalWidth"
        )
    }

    private fun getCurrentScreenWidthPx(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds.width()
            } else {
                @Suppress("DEPRECATION")
                resources.displayMetrics.widthPixels
            }
        } catch (e: Exception) {
            resources.displayMetrics.widthPixels
        }
    }

    private fun getCurrentScreenHeightPx(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds.height()
            } else {
                @Suppress("DEPRECATION")
                resources.displayMetrics.heightPixels
            }
        } catch (e: Exception) {
            resources.displayMetrics.heightPixels
        }
    }

    private fun startOverlayResizeWatcher() {
        lastScreenWidthPx = getCurrentScreenWidthPx()
        lastScreenHeightPx = getCurrentScreenHeightPx()
        lastDensityDpi = getCurrentDensityDpi()
        overlayWatcherActive = true
        resizeHandler.removeCallbacksAndMessages(null)
        resizeHandler.post(overlayResizeWatcher)
    }

    private fun stopOverlayResizeWatcher() {
        overlayWatcherActive = false
        resizeHandler.removeCallbacksAndMessages(null)
    }

    private fun hasActiveAutomationWork(): Boolean {
        return autoclickerController.isRunning() ||
            ResearchAutomationCoordinator.isRunning() ||
            (autoBoostService?.isRunning() == true) ||
            (autoDroneService?.isRunning() == true)
    }

    private fun forceRestartAppOnResolutionChange(
        oldWidth: Int,
        oldHeight: Int,
        newWidth: Int,
        newHeight: Int,
        oldDensity: Int,
        newDensity: Int
    ) {
        restartInProgress = true
        stopOverlayResizeWatcher()
        Log.w(
            TAG,
            "Display changed during runtime: " +
                "${oldWidth}x${oldHeight}@${oldDensity}dpi -> ${newWidth}x${newHeight}@${newDensity}dpi. " +
                "Restarting app."
        )
        showToast("Screen resolution changed. Restarting app...")

        stopAutoclicker()

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
            val pendingIntent = PendingIntent.getActivity(
                this,
                1001,
                launchIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.RTC,
                System.currentTimeMillis() + 350L,
                pendingIntent
            )

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()

            // Жёсткий выход процесса гарантирует полный reset всех экранных метрик/оверлея.
            Handler(Looper.getMainLooper()).postDelayed({
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(0)
            }, 150L)
        } else {
            restartInProgress = false
        }
    }

    private fun getCurrentDensityDpi(): Int {
        return try {
            resources.configuration.densityDpi
        } catch (e: Exception) {
            resources.displayMetrics.densityDpi
        }
    }

    private data class Quad(
        val factor: Float,
        val minDp: Float,
        val maxDp: Float,
        val maxRatio: Float
    )

    private fun ensureFeatureServicesInitialized() {
        val accessibility = ClickerAccessibilityService.instance ?: return
        val accessChanged = boundAccessibilityService !== accessibility

        if (accessChanged) {
            autoBoostService?.destroy()
            autoBoostService = null
            autoDroneService?.destroy()
            autoDroneService = null
            boundAccessibilityService = accessibility
        }

        if (autoBoostService == null) {
            autoBoostService = AutoBoostService(accessibility).apply {
                onBoostStateChanged = { isActive ->
                    if (!isActive) {
                        autoclickerController.resume(PAUSE_OWNER_AUTO_BOOST)
                        updateStatus(if (autoclickerController.isRunning()) getString(R.string.status_running) else getString(R.string.status_waiting))
                    }
                }
                onPauseOtherFeatures = { shouldPause ->
                    if (shouldPause) {
                        autoclickerController.pause(PAUSE_OWNER_AUTO_BOOST)
                        updateStatus(getString(R.string.status_paused_boost))
                    } else {
                        autoclickerController.resume(PAUSE_OWNER_AUTO_BOOST)
                        updateStatus(if (autoclickerController.isRunning()) getString(R.string.status_running) else getString(R.string.status_waiting))
                    }
                }
            }
        }

        if (autoDroneService == null) {
            autoDroneService = AutoDroneService(accessibility, configManager).apply {
                onDroneStateChanged = { isActive ->
                    Handler(Looper.getMainLooper()).post {
                        if (!isActive && droneModeActive) {
                            droneModeActive = false
                            val btnModeChicken = overlayView?.findViewById<Button>(R.id.btn_mode_chicken)
                            val btnModeGift = overlayView?.findViewById<Button>(R.id.btn_mode_gift)
                            val btnModeAll = overlayView?.findViewById<Button>(R.id.btn_mode_all)
                            val cfg = configManager.loadConfig()
                            applyDroneModeUiState(
                                active = droneToggleState && cfg.autoDronesEnabled,
                                btnModeChicken = btnModeChicken,
                                btnModeGift = btnModeGift,
                                btnModeAll = btnModeAll
                            )
                            updateStatus(if (autoclickerController.isRunning()) getString(R.string.status_running) else getString(R.string.status_waiting))
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Настраивает обработчики кнопок
     */
    private fun setupViews() {
        currentMode = AutoclickerService.currentMode
        
        overlayView?.let { view ->
            // Кнопки управления окном
            val btnMinimize = view.findViewById<View>(R.id.btn_minimize)
            val btnClose = view.findViewById<View>(R.id.btn_close)
            
            // Основные кнопки
            val btnStart = view.findViewById<Button>(R.id.btn_start)
            val btnStop = view.findViewById<Button>(R.id.btn_stop)
            
            // Кнопки режимов
            val btnModeChicken = view.findViewById<Button>(R.id.btn_mode_chicken)
            val btnModeGift = view.findViewById<Button>(R.id.btn_mode_gift)
            val btnModeAll = view.findViewById<Button>(R.id.btn_mode_all)
            
            // Статус
            val statusView = view.findViewById<TextView>(R.id.tv_status)
            val indicatorView = view.findViewById<View>(R.id.status_indicator)
            statusViewRef = WeakReference(statusView)
            indicatorViewRef = WeakReference(indicatorView)
            
            // Обновляем текущий статус
            statusView.text = currentStatus
            
            // Кнопка минимизации
            btnMinimize?.setOnClickListener {
                toggleMinimize()
            }
            
            // Кнопка закрытия
            btnClose?.setOnClickListener {
                stopAutoclicker()
                stopSelf()
            }
            
            // Кнопка старт
            btnStart?.setOnClickListener {
                // Проверяем режим дронов
                if (droneModeActive) {
                    ensureFeatureServicesInitialized()
                    startOverlayResizeWatcher()
                    // Режим дронов - запускаем свайпы и сворачиваем оверлей
                    // В режиме дронов НЕ запускаем основной AutoclickerService,
                    // иначе возможны лишние клики (например, по кнопке куриц внизу).
                    autoDroneService?.start()
                    btnStart.visibility = View.GONE
                    btnStop.visibility = View.VISIBLE
                    updateStatusIndicator(true)
                    updateStatus(getString(R.string.status_drone_mode))
                    // Сворачиваем оверлей
                    if (!isMinimized) {
                        toggleMinimize()
                    }
                    return@setOnClickListener
                }
                
                startAutoclicker()
                startOverlayResizeWatcher()
                btnStart.visibility = View.GONE
                btnStop.visibility = View.VISIBLE
                updateStatusIndicator(true)
                
                // Если авто-исследования включены в toggle - запускаем их
                if (toggleResearch?.isChecked == true) {
                    autoclickerController.startResearch(this@OverlayService)
                }
                
                // Если авто-буст включен в toggle - запускаем его
                if (toggleBoost2x?.isChecked == true) {
                    ensureFeatureServicesInitialized()
                    val intervalMin = configManager.loadConfig().autoBoost2xIntervalMin.coerceIn(1, 60)
                    autoBoostService?.start(intervalMin)
                }
            }
            
            // Кнопка стоп
            btnStop?.setOnClickListener {
                stopAutoclicker()
                btnStop.visibility = View.GONE
                btnStart.visibility = View.VISIBLE
                updateStatusIndicator(false)
            }
            
            // Кнопки режимов
            btnModeChicken?.setOnClickListener {
                setMode(
                    mode = ClickerMode.CHICKEN_ONLY,
                    selectedBtn = btnModeChicken,
                    otherBtns = listOfNotNull(btnModeGift, btnModeAll)
                )
            }
            
            btnModeGift?.setOnClickListener {
                setMode(
                    mode = ClickerMode.GIFT_ONLY,
                    selectedBtn = btnModeGift,
                    otherBtns = listOfNotNull(btnModeChicken, btnModeAll)
                )
            }
            
            btnModeAll?.setOnClickListener {
                setMode(
                    mode = ClickerMode.ALL,
                    selectedBtn = btnModeAll,
                    otherBtns = listOfNotNull(btnModeChicken, btnModeGift)
                )
            }
            
            applyModeSelectionUi(currentMode, btnModeChicken, btnModeGift, btnModeAll)
            
            // Настраиваем авто-фичи
            setupAutoFeatures(view)
        }
    }
    
    /**
     * Настраивает авто-фичи
     */
    private fun setupAutoFeatures(view: View) {
        val config = configManager.loadConfig()
        
        // Получаем ссылки на переключатели
        toggleResearch = view.findViewById(R.id.toggle_research)
        toggleBoost2x = view.findViewById(R.id.toggle_boosts)
        toggleDrones = view.findViewById(R.id.toggle_drones)
        val btnModeChicken = view.findViewById<Button>(R.id.btn_mode_chicken)
        val btnModeGift = view.findViewById<Button>(R.id.btn_mode_gift)
        val btnModeAll = view.findViewById<Button>(R.id.btn_mode_all)
        
        val layoutAutoFeatures = view.findViewById<LinearLayout>(R.id.layout_auto_features)
        
        var hasEnabledFeatures = false
        
        // Показываем только включенные в настройках фичи
        if (config.autoResearchEnabled) {
            toggleResearch?.visibility = View.VISIBLE
            toggleResearch?.isChecked = researchToggleState
            updateToggleVisual(toggleResearch)
            toggleResearch?.setOnCheckedChangeListener { _, isChecked ->
                researchToggleState = isChecked
                updateToggleVisual(toggleResearch)
                Log.d(TAG, "Research toggle changed: $isChecked, droneMode=$droneModeActive, autoclicker=${autoclickerController.isRunning()}")
                
                // Проверяем что режим дронов не активен
                if (isChecked && droneModeActive) {
                    showToast(getString(R.string.toast_disable_drones_first))
                    toggleResearch?.isChecked = false
                    return@setOnCheckedChangeListener
                }
                
                // Запускаем/останавливаем авто-исследования только если основной автокликер работает
                if (isChecked && autoclickerController.isRunning()) {
                    autoclickerController.startResearch(this@OverlayService)
                } else if (!isChecked) {
                    autoclickerController.stopResearch()
                }
                Log.d(TAG, "Auto research: $isChecked (will start with main service)")
            }
            hasEnabledFeatures = true
        } else {
            toggleResearch?.visibility = View.GONE
        }
        
        // ===== АВТО-БУСТ 2x ДЕНЬГИ =====
        if (config.autoBoost2xEnabled) {
            toggleBoost2x?.visibility = View.VISIBLE
            toggleBoost2x?.textOn = getString(R.string.toggle_boost_on)
            toggleBoost2x?.textOff = getString(R.string.toggle_boost_off)
            toggleBoost2x?.isChecked = boostToggleState
            updateToggleVisual(toggleBoost2x)
            
            toggleBoost2x?.setOnCheckedChangeListener { _, isChecked ->
                ensureFeatureServicesInitialized()
                boostToggleState = isChecked
                updateToggleVisual(toggleBoost2x)
                Log.d(TAG, "Auto boost 2x toggle: $isChecked")
                
                // Проверяем что режим дронов не активен
                if (isChecked && droneModeActive) {
                    showToast(getString(R.string.toast_disable_drones_first))
                    toggleBoost2x?.isChecked = false
                    return@setOnCheckedChangeListener
                }
                
                // Запускаем авто-буст только если основной автокликер уже работает
                if (isChecked && autoclickerController.isRunning()) {
                    val intervalMin = config.autoBoost2xIntervalMin.coerceIn(1, 60)
                    autoBoostService?.start(intervalMin)
                    showToast(getString(R.string.toast_boost_started, intervalMin))
                } else if (!isChecked) {
                    // Останавливаем авто-буст
                    autoBoostService?.stop()
                    autoclickerController.resume(PAUSE_OWNER_AUTO_BOOST)
                    updateStatus(if (autoclickerController.isRunning()) getString(R.string.status_running) else getString(R.string.status_waiting))
                }
            }
            
            hasEnabledFeatures = true
        } else {
            toggleBoost2x?.visibility = View.GONE
        }
        
        // ===== АВТО-ДРОНЫ =====
        if (config.autoDronesEnabled) {
            toggleDrones?.visibility = View.VISIBLE
            toggleDrones?.textOn = getString(R.string.toggle_drones_on)
            toggleDrones?.textOff = getString(R.string.toggle_drones_off)
            toggleDrones?.isChecked = droneToggleState
            updateToggleVisual(toggleDrones)
            
            toggleDrones?.setOnCheckedChangeListener { _, isChecked ->
                ensureFeatureServicesInitialized()
                droneToggleState = isChecked
                updateToggleVisual(toggleDrones)
                Log.d(TAG, "Auto drones toggle: $isChecked")
                
                if (isChecked) {
                    // Проверяем что автокликер не запущен с другими функциями
                    if (autoclickerController.isRunning() && !droneModeActive) {
                        showToast(getString(R.string.toast_stop_first))
                        toggleDrones?.isChecked = false
                        return@setOnCheckedChangeListener
                    }
                    
                    // Режим дронов - блокируем другие функции
                    droneModeActive = true
                    // Выключаем другие toggles (сохраняем состояние)
                    researchToggleState = false
                    boostToggleState = false
                    toggleResearch?.isChecked = false
                    toggleBoost2x?.isChecked = false
                    updateToggleVisual(toggleResearch)
                    updateToggleVisual(toggleBoost2x)
                    // Блокируем другие toggles (делаем серыми и некликабельными)
                    toggleResearch?.isEnabled = false
                    toggleResearch?.alpha = 0.3f
                    toggleBoost2x?.isEnabled = false
                    toggleBoost2x?.alpha = 0.3f
                    updateToggleVisual(toggleResearch)
                    updateToggleVisual(toggleBoost2x)
                    // Блокируем кнопки режимов
                    btnModeChicken?.isEnabled = false
                    btnModeChicken?.alpha = 0.3f
                    btnModeGift?.isEnabled = false
                    btnModeGift?.alpha = 0.3f
                    btnModeAll?.isEnabled = false
                    btnModeAll?.alpha = 0.3f
                    showToast(getString(R.string.toast_drone_mode_start))
                } else {
                    // Выключаем режим дронов
                    droneModeActive = false
                    droneToggleState = false
                    autoDroneService?.stop()
                    // Восстанавливаем другие toggles
                    toggleResearch?.isEnabled = true
                    toggleResearch?.alpha = 1.0f
                    toggleBoost2x?.isEnabled = true
                    toggleBoost2x?.alpha = 1.0f
                    updateToggleVisual(toggleResearch)
                    updateToggleVisual(toggleBoost2x)
                    // Восстанавливаем кнопки режимов
                    btnModeChicken?.isEnabled = true
                    btnModeChicken?.alpha = 1.0f
                    btnModeGift?.isEnabled = true
                    btnModeGift?.alpha = 1.0f
                    btnModeAll?.isEnabled = true
                    btnModeAll?.alpha = 1.0f
                }
            }
            
            hasEnabledFeatures = true
        } else {
            toggleDrones?.visibility = View.GONE
        }
        
        // Скрываем блок кнопок бустов (не используется в новой версии)
        val layoutBoostQuick = view.findViewById<LinearLayout>(R.id.layout_boost_quick)
        layoutBoostQuick?.visibility = View.GONE
        
        // Показываем блок авто-фич только если есть включенные
        layoutAutoFeatures?.visibility = if (hasEnabledFeatures) View.VISIBLE else View.GONE
        applyDroneModeUiState(
            active = droneToggleState && config.autoDronesEnabled,
            btnModeChicken = btnModeChicken,
            btnModeGift = btnModeGift,
            btnModeAll = btnModeAll
        )
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag() {
        overlayView?.setOnTouchListener { _, event ->
            val layoutParams = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, layoutParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Проверяем, был ли это клик (небольшое перемещение)
                    val deltaX = abs(event.rawX - initialTouchX)
                    val deltaY = abs(event.rawY - initialTouchY)
                    if (deltaX < 10 && deltaY < 10 && isMinimized) {
                        // Это был клик на свернутый оверлей - разворачиваем
                        toggleMinimize()
                        overlayView?.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * Разворачивает оверлей
     * Сворачивает оверлей
     */
    private fun toggleMinimize() {
        isMinimized = !isMinimized
        
        overlayView?.let { view ->
            val mainContent = view.findViewById<LinearLayout>(R.id.overlay_content)
            val minimizedView = view.findViewById<LinearLayout>(R.id.minimized_view)
            
            if (isMinimized) {
                // Сворачиваем оверлей
                mainContent?.visibility = View.GONE
                minimizedView?.visibility = View.VISIBLE
                params?.width = 60
                params?.height = 60
            } else {
                // Разворачиваем оверлей
                mainContent?.visibility = View.VISIBLE
                minimizedView?.visibility = View.GONE
                params?.width = WindowManager.LayoutParams.WRAP_CONTENT
                params?.height = WindowManager.LayoutParams.WRAP_CONTENT
            }
            
            windowManager.updateViewLayout(view, params)
        }
    }
    
    private fun setMode(mode: ClickerMode, selectedBtn: Button, otherBtns: List<Button>) {
        currentMode = mode
        autoclickerController.setMode(mode)
        
        // Обновляем UI - выбранная кнопка оранжевая, остальные серые
        selectedBtn.backgroundTintList = ColorStateList.valueOf("#FF6B35".toColorInt())
        otherBtns.forEach { it.backgroundTintList = ColorStateList.valueOf("#424242".toColorInt()) }
        
        // Если сервис запущен, обновляем режим
        if (autoclickerController.isRunning()) {
            val intent = Intent(this, AutoclickerService::class.java).apply {
                action = AutoclickerService.ACTION_SET_MODE
                putExtra(AutoclickerService.EXTRA_MODE, mode.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        
        Log.d(TAG, "Mode changed to: $mode")
    }

    private fun applyModeSelectionUi(
        mode: ClickerMode,
        btnModeChicken: Button?,
        btnModeGift: Button?,
        btnModeAll: Button?
    ) {
        val selected = "#FF6B35".toColorInt()
        val normal = "#424242".toColorInt()
        btnModeChicken?.backgroundTintList = ColorStateList.valueOf(if (mode == ClickerMode.CHICKEN_ONLY) selected else normal)
        btnModeGift?.backgroundTintList = ColorStateList.valueOf(if (mode == ClickerMode.GIFT_ONLY) selected else normal)
        btnModeAll?.backgroundTintList = ColorStateList.valueOf(if (mode == ClickerMode.ALL) selected else normal)
    }

    private fun applyDroneModeUiState(
        active: Boolean,
        btnModeChicken: Button?,
        btnModeGift: Button?,
        btnModeAll: Button?
    ) {
        droneModeActive = active
        if (active) {
            researchToggleState = false
            boostToggleState = false
            toggleResearch?.isChecked = false
            toggleBoost2x?.isChecked = false
        }
        toggleResearch?.isEnabled = !active
        toggleResearch?.alpha = if (active) 0.3f else 1.0f
        toggleBoost2x?.isEnabled = !active
        toggleBoost2x?.alpha = if (active) 0.3f else 1.0f
        btnModeChicken?.isEnabled = !active
        btnModeChicken?.alpha = if (active) 0.3f else 1.0f
        btnModeGift?.isEnabled = !active
        btnModeGift?.alpha = if (active) 0.3f else 1.0f
        btnModeAll?.isEnabled = !active
        btnModeAll?.alpha = if (active) 0.3f else 1.0f
    }
    
    private fun startAutoclicker() {
        val intent = Intent(this, AutoclickerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateStatus(getString(R.string.status_running))
    }
    
    private fun stopAutoclicker() {
        stopOverlayResizeWatcher()

        // Останавливаем авто-исследования если работают
        autoclickerController.stopResearch()
        
        // Останавливаем авто-буст если работает
        if (autoBoostService?.isRunning() == true) {
            autoBoostService?.stop()
        }
        
        // Останавливаем дронов если работают
        if (autoDroneService?.isRunning() == true) {
            autoDroneService?.stop()
        }

        // Сохраняем выбранные пользователем toggles, не сбрасываем их на STOP.
        // Применяем актуальную доступность элементов для drone-режима.
        val btnModeChicken = overlayView?.findViewById<Button>(R.id.btn_mode_chicken)
        val btnModeGift = overlayView?.findViewById<Button>(R.id.btn_mode_gift)
        val btnModeAll = overlayView?.findViewById<Button>(R.id.btn_mode_all)
        val cfg = configManager.loadConfig()
        applyDroneModeUiState(
            active = droneToggleState && cfg.autoDronesEnabled,
            btnModeChicken = btnModeChicken,
            btnModeGift = btnModeGift,
            btnModeAll = btnModeAll
        )
        updateToggleVisual(toggleResearch)
        updateToggleVisual(toggleBoost2x)
        updateToggleVisual(toggleDrones)
        
        // Останавливаем основной автокликер
        val intent = Intent(this, AutoclickerService::class.java)
        stopService(intent)
        updateStatus(getString(R.string.status_stopped))
        Log.d(TAG, "All services stopped")
    }
    
    private fun updateStatusIndicator(isRunning: Boolean) {
        indicatorViewRef?.get()?.let { indicator ->
            if (isRunning) {
                indicator.setBackgroundColor(getColor(android.R.color.holo_green_light))
            } else {
                indicator.setBackgroundColor(getColor(android.R.color.holo_orange_light))
            }
        }
    }
    
    /**
     * Удаляет оверлей
     */
    private fun removeOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
        statusViewRef = null
        indicatorViewRef = null
    }
    
    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateToggleVisual(toggle: ToggleButton?) {
        toggle ?: return
        val colorRes = if (toggle.isChecked) android.R.color.white else android.R.color.darker_gray
        toggle.setTextColor(ContextCompat.getColor(this, colorRes))
    }
}
