package com.egginc.autoclicker

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.view.View
import android.view.inputmethod.EditorInfo
import android.text.TextWatcher
import android.text.Editable
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.egginc.autoclicker.service.OverlayService
import com.egginc.autoclicker.utils.ConfigManager
import com.egginc.autoclicker.utils.LocaleHelper
import com.egginc.autoclicker.utils.PermissionHelper
import com.egginc.autoclicker.utils.StringManager
import android.widget.ImageButton

/**
 * Главная Activity приложения
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAppSettingsStatus: TextView
    private lateinit var tvAppSettingsHint: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvPermissionProgress: TextView
    private lateinit var progressPermissionSteps: ProgressBar
    private lateinit var btnRequestOverlay: Button
    private lateinit var btnOpenAppSettings: Button
    private lateinit var btnRequestAccessibility: Button
    private lateinit var btnNextPermissionStep: Button
    private lateinit var btnStartOverlay: Button
    private lateinit var etSwipeDuration: EditText
    private lateinit var etRestDuration: EditText
    private lateinit var cbUseCV: CheckBox
    private lateinit var etGiftInterval: EditText
    private lateinit var tvGiftModeDesc: TextView
    private lateinit var tvGiftIntervalHint: TextView
    
    // Умный режим куриц
    private lateinit var cbSmartChicken: CheckBox
    private lateinit var layoutManualSettings: LinearLayout
    private lateinit var layoutCooldownSetting: LinearLayout
    private lateinit var etRedCooldown: EditText
    private lateinit var tvCooldownHint: TextView

    private lateinit var permissionHelper: PermissionHelper
    private lateinit var configManager: ConfigManager

    companion object {
        private const val REQUEST_CODE_OVERLAY = 1001
        private const val PREFS_PERMISSION_FLOW = "permission_flow"
        private const val KEY_APP_SETTINGS_VISITED = "app_settings_visited"
        private const val KEY_ACCESSIBILITY_PRIMED = "accessibility_primed"
    }

    private enum class StartButtonMode {
        START,
        RESTART,
        RESTARTING
    }

    private enum class PermissionStep {
        OVERLAY,
        ACCESSIBILITY_PRIME,
        APP_SETTINGS,
        ACCESSIBILITY_ENABLE,
        DONE
    }
    
    override fun attachBaseContext(newBase: Context) {
        val context = LocaleHelper.setLocale(newBase)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        limitContentWidth()

        permissionHelper = PermissionHelper(this)
        configManager = ConfigManager(this)

        // Инициализация OpenCV
        try {
            System.loadLibrary("opencv_java4")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("MainActivity", "OpenCV not loaded", e)
        }

        initViews()
        loadSettings()
        updatePermissionStatus()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateButtonRunnable = object : Runnable {
        override fun run() {
            updateOverlayButtonState()
            handler.postDelayed(this, 1000) // Обновляем раз в 1 сек, чтобы не дёргать UI лишний раз
        }
    }
    private var lastStartButtonMode: StartButtonMode? = null

    override fun onResume() {
        super.onResume()
        // Перезагружаем язык
        StringManager.loadLanguage(this)
        updateUITexts()
        // Перечитываем конфиг, чтобы подхватить изменения из экрана настроек
        loadSettings()
        updatePermissionStatus()
        // Запускаем периодическое обновление кнопки
        handler.post(updateButtonRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        // Сохранение интервала подарков при уходе с экрана
        saveGiftInterval()
        // Останавливаем обновление
        handler.removeCallbacks(updateButtonRunnable)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateButtonRunnable)
    }

    private fun initViews() {
        tvOverlayStatus = findViewById(R.id.tv_overlay_status)
        tvAppSettingsStatus = findViewById(R.id.tv_app_settings_status)
        tvAppSettingsHint = findViewById(R.id.tv_app_settings_hint)
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        tvPermissionProgress = findViewById(R.id.tv_permission_progress)
        progressPermissionSteps = findViewById(R.id.progress_permission_steps)
        btnRequestOverlay = findViewById(R.id.btn_request_overlay)
        btnOpenAppSettings = findViewById(R.id.btn_open_app_settings)
        btnRequestAccessibility = findViewById(R.id.btn_request_accessibility)
        btnNextPermissionStep = findViewById(R.id.btn_next_permission_step)
        btnStartOverlay = findViewById(R.id.btn_start_overlay)
        etSwipeDuration = findViewById(R.id.et_swipe_duration)
        etRestDuration = findViewById(R.id.et_rest_duration)
        cbUseCV = findViewById(R.id.cb_use_cv)
        etGiftInterval = findViewById(R.id.et_gift_interval)
        tvGiftModeDesc = findViewById(R.id.tv_gift_mode_desc)
        tvGiftIntervalHint = findViewById(R.id.tv_gift_interval_hint)
        
        // Умный режим куриц
        cbSmartChicken = findViewById(R.id.cb_smart_chicken)
        layoutManualSettings = findViewById(R.id.layout_manual_settings)
        layoutCooldownSetting = findViewById(R.id.layout_cooldown_setting)
        etRedCooldown = findViewById(R.id.et_red_cooldown)
        tvCooldownHint = findViewById(R.id.tv_cooldown_hint)
        
        // Обработчик переключения умного режима
        cbSmartChicken.setOnCheckedChangeListener { _, isChecked ->
            updateSmartModeUI(isChecked)
        }
        
        // Обработка изменения режима CV подарков
        cbUseCV.setOnCheckedChangeListener { _, _ ->
            updateGiftIntervalHint()
        }
        
        // Обработка изменения интервала подарков
        etGiftInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                updateGiftIntervalHint()
                saveGiftInterval()
            }
        }
        
        // TextWatcher для автосохранения при вводе (с задержкой)
        etGiftInterval.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Сохраняем сразу при изменении текста
                val text = s?.toString()
                if (!text.isNullOrEmpty()) {
                    val interval = text.toIntOrNull()
                    if (interval != null && interval in 10..600) {
                        updateGiftIntervalHint()
                        saveGiftInterval()
                    }
                }
            }
        })
        
        // Обработка кнопки "Готово" (Done) на клавиатуре
        etGiftInterval.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                updateGiftIntervalHint()
                saveGiftInterval()
                // Скрываем клавиатуру
                etGiftInterval.clearFocus()
                true
            } else {
                false
            }
        }

        btnRequestOverlay.setOnClickListener {
            requestOverlayPermission()
        }

        btnRequestAccessibility.setOnClickListener {
            requestAccessibilityPermission()
        }

        btnOpenAppSettings.setOnClickListener {
            openAppSettings()
        }

        btnNextPermissionStep.setOnClickListener {
            openNextPermissionStep()
        }

        btnStartOverlay.setOnClickListener {
            startOverlayWithChecks()
        }
        
        // Кнопка настроек
        findViewById<ImageButton>(R.id.btn_settings)?.setOnClickListener {
            openSettings()
        }
    }
    
    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    /**
     * Загрузка настроек
     */
    private fun loadSettings() {
        val config = configManager.loadConfig()
        etSwipeDuration.setText((config.chickenSwipeDurationMs / 1000).toString())
        etRestDuration.setText((config.chickenRestDurationMs / 1000).toString())
        cbUseCV.isChecked = config.useCVForGift
        
        // Интервал подарков (мс -> сек)
        val giftIntervalSec = (config.giftCheckIntervalMs / 1000).toInt()
        etGiftInterval.setText(giftIntervalSec.toString())
        updateGiftIntervalHint()
        
        // Умный режим куриц
        cbSmartChicken.isChecked = config.smartChickenMode
        etRedCooldown.setText((config.redIndicatorCooldownMs / 1000).toString())
        updateSmartModeUI(config.smartChickenMode)
    }
    
    /**
     * Обновление подсказки интервала подарков
     */
    private fun updateGiftIntervalHint() {
        val intervalSec = etGiftInterval.text.toString().toIntOrNull() ?: 60
        val useCV = cbUseCV.isChecked
        
        if (useCV) {
            tvGiftModeDesc.text = getString(R.string.cv_desc)
            tvGiftIntervalHint.text = getString(R.string.gift_check_every, intervalSec)
        } else {
            tvGiftModeDesc.text = getString(R.string.gift_no_cv_desc)
            tvGiftIntervalHint.text = getString(R.string.gift_click_coords, intervalSec)
        }
    }
    
    /**
     * Сохранение интервала подарков
     */
    private fun saveGiftInterval() {
        val config = configManager.loadConfig()
        val intervalSec = etGiftInterval.text.toString().toIntOrNull() ?: 60
        // Ограничиваем разумными пределами (10-600 сек)
        val clampedInterval = intervalSec.coerceIn(10, 600)
        config.giftCheckIntervalMs = clampedInterval * 1000L
        configManager.saveConfig(config)
        
        // Обновляем UI если значение было скорректировано
        if (intervalSec != clampedInterval) {
            etGiftInterval.setText(clampedInterval.toString())
        }
    }
    
    /**
     * Обновление UI умного режима
     */
    private fun updateSmartModeUI(enabled: Boolean) {
        if (enabled) {
            // Умный режим включен - обычные настройки серые
            layoutManualSettings.alpha = 0.4f
            etSwipeDuration.isEnabled = false
            etRestDuration.isEnabled = false
            
            // Настройка отката активна
            layoutCooldownSetting.alpha = 1.0f
            layoutCooldownSetting.isEnabled = true
            etRedCooldown.isEnabled = true
            tvCooldownHint.alpha = 1.0f
        } else {
            // Умный режим выключен - обычные настройки активны
            layoutManualSettings.alpha = 1.0f
            etSwipeDuration.isEnabled = true
            etRestDuration.isEnabled = true
            
            // Настройка отката скрыта/серая
            layoutCooldownSetting.alpha = 0.5f
            layoutCooldownSetting.isEnabled = false
            etRedCooldown.isEnabled = false
            tvCooldownHint.alpha = 0.5f
        }
    }

    /**
     * Сохранение настроек
     */
    private fun saveSettings() {
        val config = configManager.loadConfig()
        
        // Удержание и отдых куриц (1-60 сек)
        val swipeDuration = etSwipeDuration.text.toString().toIntOrNull() ?: 4
        val restDuration = etRestDuration.text.toString().toIntOrNull() ?: 8
        config.chickenSwipeDurationMs = swipeDuration.coerceIn(1, 60) * 1000L
        config.chickenRestDurationMs = restDuration.coerceIn(1, 60) * 1000L
        
        config.useCVForGift = cbUseCV.isChecked
        
        // Интервал подарков (10-600 сек)
        val giftIntervalSec = etGiftInterval.text.toString().toIntOrNull() ?: 60
        config.giftCheckIntervalMs = giftIntervalSec.coerceIn(10, 600) * 1000L
        
        // Умный режим куриц
        config.smartChickenMode = cbSmartChicken.isChecked
        
        // Откат после красного (1-60 сек)
        val redCooldown = etRedCooldown.text.toString().toIntOrNull() ?: 10
        config.redIndicatorCooldownMs = redCooldown.coerceIn(1, 60) * 1000L
        
        configManager.saveConfig(config)
    }

    /**
     * Проверка разрешений
     */
    private fun updatePermissionStatus() {
        val overlayGranted = permissionHelper.canDrawOverlays()
        val accessibilityGranted = permissionHelper.isAccessibilityServiceEnabled()
        val flowPrefs = getSharedPreferences(PREFS_PERMISSION_FLOW, MODE_PRIVATE)
        val appSettingsVisited = flowPrefs.getBoolean(KEY_APP_SETTINGS_VISITED, false)
        val accessibilityPrimed = flowPrefs.getBoolean(KEY_ACCESSIBILITY_PRIMED, false) || accessibilityGranted
        val appSettingsStepRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val appSettingsStepDone = !appSettingsStepRequired || accessibilityGranted || appSettingsVisited

        // Обновляем UI для оверлея
        if (overlayGranted) {
            tvOverlayStatus.text = StringManager.get("overlay_on")
            tvOverlayStatus.setTextColor(getColor(android.R.color.holo_green_light))
            btnRequestOverlay.isEnabled = false
            btnRequestOverlay.text = StringManager.get("granted")
        } else {
            tvOverlayStatus.text = StringManager.get("overlay_off")
            tvOverlayStatus.setTextColor(getColor(android.R.color.holo_red_light))
            btnRequestOverlay.isEnabled = true
            btnRequestOverlay.text = StringManager.get("grant")
        }

        if (overlayGranted) {
            btnOpenAppSettings.isEnabled = true
            btnOpenAppSettings.alpha = 1.0f
            btnOpenAppSettings.text = getString(R.string.open_settings)
        } else {
            btnOpenAppSettings.isEnabled = false
            btnOpenAppSettings.alpha = 0.5f
            btnOpenAppSettings.text = getString(R.string.open_settings)
        }

        if (appSettingsStepDone) {
            tvAppSettingsStatus.text = getString(R.string.app_settings_status_done)
            tvAppSettingsStatus.setTextColor(getColor(android.R.color.holo_green_light))
        } else if (!overlayGranted) {
            tvAppSettingsStatus.text = getString(R.string.app_settings_status_locked)
            tvAppSettingsStatus.setTextColor(getColor(android.R.color.darker_gray))
        } else {
            tvAppSettingsStatus.text = getString(R.string.app_settings_status_required)
            tvAppSettingsStatus.setTextColor(getColor(android.R.color.holo_orange_light))
        }

        val showAppSettingsHint = appSettingsStepRequired && overlayGranted && !accessibilityGranted
        tvAppSettingsHint.visibility = if (showAppSettingsHint) View.VISIBLE else View.GONE

        val accessibilityStepUnlocked = overlayGranted
        if (accessibilityGranted) {
            tvAccessibilityStatus.text = StringManager.get("accessibility_on")
            tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_green_light))
            btnRequestAccessibility.isEnabled = false
            btnRequestAccessibility.alpha = 1.0f
            btnRequestAccessibility.text = StringManager.get("granted")
        } else if (!accessibilityStepUnlocked) {
            tvAccessibilityStatus.text = getString(R.string.accessibility_locked)
            tvAccessibilityStatus.setTextColor(getColor(android.R.color.darker_gray))
            btnRequestAccessibility.isEnabled = false
            btnRequestAccessibility.alpha = 0.5f
            btnRequestAccessibility.text = StringManager.get("grant")
        } else {
            tvAccessibilityStatus.text = StringManager.get("accessibility_off")
            tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_red_light))
            btnRequestAccessibility.isEnabled = true
            btnRequestAccessibility.alpha = 1.0f
            btnRequestAccessibility.text = StringManager.get("grant")
        }

        val doneCount = listOf(overlayGranted, accessibilityPrimed, appSettingsStepDone, accessibilityGranted).count { it }
        tvPermissionProgress.text = getString(R.string.permission_progress_format, doneCount)
        progressPermissionSteps.max = 4
        progressPermissionSteps.progress = doneCount
        tvPermissionProgress.setTextColor(
            getColor(
                if (doneCount == 4) android.R.color.holo_green_light else android.R.color.darker_gray
            )
        )
        when (getNextPermissionStep(overlayGranted, accessibilityPrimed, appSettingsStepDone, accessibilityGranted)) {
            PermissionStep.OVERLAY -> {
                btnNextPermissionStep.isEnabled = true
                btnNextPermissionStep.alpha = 1.0f
                btnNextPermissionStep.text = getString(R.string.permission_next_overlay)
            }
            PermissionStep.ACCESSIBILITY_PRIME -> {
                btnNextPermissionStep.isEnabled = true
                btnNextPermissionStep.alpha = 1.0f
                btnNextPermissionStep.text = getString(R.string.permission_next_accessibility_prime)
            }
            PermissionStep.APP_SETTINGS -> {
                btnNextPermissionStep.isEnabled = true
                btnNextPermissionStep.alpha = 1.0f
                btnNextPermissionStep.text = getString(R.string.permission_next_app_settings)
            }
            PermissionStep.ACCESSIBILITY_ENABLE -> {
                btnNextPermissionStep.isEnabled = true
                btnNextPermissionStep.alpha = 1.0f
                btnNextPermissionStep.text = getString(R.string.permission_next_accessibility_enable)
            }
            PermissionStep.DONE -> {
                btnNextPermissionStep.isEnabled = false
                btnNextPermissionStep.alpha = 0.5f
                btnNextPermissionStep.text = getString(R.string.permissions_ready)
            }
        }

        updateOverlayButtonState()
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_CODE_OVERLAY)
        }
    }

    private fun requestAccessibilityPermission() {
        getSharedPreferences(PREFS_PERMISSION_FLOW, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACCESSIBILITY_PRIMED, true)
            .apply()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        Toast.makeText(
            this,
            getString(R.string.accessibility_enable_hint),
            Toast.LENGTH_LONG
        ).show()
        startActivity(intent)
    }

    private fun openAppSettings() {
        getSharedPreferences(PREFS_PERMISSION_FLOW, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APP_SETTINGS_VISITED, true)
            .apply()
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:$packageName".toUri()
        }
        startActivity(intent)
    }

    private fun getNextPermissionStep(
        overlayGranted: Boolean,
        accessibilityPrimed: Boolean,
        appSettingsStepDone: Boolean,
        accessibilityGranted: Boolean
    ): PermissionStep {
        return when {
            !overlayGranted -> PermissionStep.OVERLAY
            !accessibilityPrimed -> PermissionStep.ACCESSIBILITY_PRIME
            !appSettingsStepDone -> PermissionStep.APP_SETTINGS
            !accessibilityGranted -> PermissionStep.ACCESSIBILITY_ENABLE
            else -> PermissionStep.DONE
        }
    }

    private fun openNextPermissionStep() {
        val overlayGranted = permissionHelper.canDrawOverlays()
        val accessibilityGranted = permissionHelper.isAccessibilityServiceEnabled()
        val flowPrefs = getSharedPreferences(PREFS_PERMISSION_FLOW, MODE_PRIVATE)
        val appSettingsVisited = flowPrefs.getBoolean(KEY_APP_SETTINGS_VISITED, false)
        val accessibilityPrimed = flowPrefs.getBoolean(KEY_ACCESSIBILITY_PRIMED, false) || accessibilityGranted
        val appSettingsStepRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val appSettingsStepDone = !appSettingsStepRequired || accessibilityGranted || appSettingsVisited

        when (getNextPermissionStep(overlayGranted, accessibilityPrimed, appSettingsStepDone, accessibilityGranted)) {
            PermissionStep.OVERLAY -> requestOverlayPermission()
            PermissionStep.ACCESSIBILITY_PRIME -> requestAccessibilityPermission()
            PermissionStep.APP_SETTINGS -> openAppSettings()
            PermissionStep.ACCESSIBILITY_ENABLE -> requestAccessibilityPermission()
            PermissionStep.DONE -> Toast.makeText(this, getString(R.string.permissions_ready), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Запуск оверлей сервиса
     */
    private fun startOverlayService() {
        // Защита от спама - проверяем не запущен ли уже
        if (OverlayService.isRunning) {
            return
        }
        
        Log.d("MainActivity", "Starting overlay service...")
        
        Log.d("MainActivity", "Launching OverlayService...")
        val intent = Intent(this, OverlayService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Log.d("MainActivity", "OverlayService started successfully")
            
            // Обновляем UI кнопки
            updateOverlayButtonState()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start OverlayService", e)
            Toast.makeText(
                this,
                getString(R.string.launch_error_message, e.message ?: getString(R.string.error_unknown)),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private var isRestartingOverlay = false

    private fun startOverlayWithChecks() {
        if (!permissionHelper.canDrawOverlays()) {
            Toast.makeText(this, getString(R.string.toast_permission_overlay), Toast.LENGTH_SHORT).show()
            return
        }
        if (!permissionHelper.isAccessibilityServiceEnabled()) {
            Toast.makeText(this, getString(R.string.toast_permission_accessibility), Toast.LENGTH_SHORT).show()
            return
        }
        saveSettings()
        startOverlayService()
    }
    
    private fun updateOverlayButtonState() {
        val hasPermissions = permissionHelper.canDrawOverlays() && permissionHelper.isAccessibilityServiceEnabled()
        val mode = when {
            OverlayService.isRunning && isRestartingOverlay -> StartButtonMode.RESTARTING
            OverlayService.isRunning -> StartButtonMode.RESTART
            else -> StartButtonMode.START
        }

        renderStartButtonState(hasPermissions, mode)
    }

    private fun renderStartButtonState(hasPermissions: Boolean, mode: StartButtonMode) {
        if (lastStartButtonMode != mode) {
            when (mode) {
                StartButtonMode.START -> {
                    btnStartOverlay.text = StringManager.get("start")
                    btnStartOverlay.setOnClickListener { startOverlayWithChecks() }
                }
                StartButtonMode.RESTART, StartButtonMode.RESTARTING -> {
                    btnStartOverlay.text = StringManager.get("restart_overlay")
                    btnStartOverlay.setOnClickListener {
                        if (isRestartingOverlay) return@setOnClickListener
                        saveSettings()
                        isRestartingOverlay = true
                        updateOverlayButtonState()
                        stopOverlayService()

                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (!isFinishing) {
                                startOverlayService()
                                isRestartingOverlay = false
                                updateOverlayButtonState()
                            }
                        }, 500)
                    }
                }
            }
            lastStartButtonMode = mode
        }

        val enabled = hasPermissions && mode != StartButtonMode.RESTARTING
        val alpha = when {
            !hasPermissions -> 0.5f
            mode == StartButtonMode.RESTART -> 0.8f
            mode == StartButtonMode.RESTARTING -> 0.5f
            else -> 1.0f
        }
        btnStartOverlay.isEnabled = enabled
        btnStartOverlay.alpha = alpha
    }

    private fun limitContentWidth() {
        val container = findViewById<View>(R.id.main_content_container) ?: return
        val maxWidthPx = (560 * resources.displayMetrics.density).toInt()
        val screenWidth = resources.displayMetrics.widthPixels
        val targetWidth = minOf(maxWidthPx, screenWidth - (24 * resources.displayMetrics.density).toInt())
        val lp = container.layoutParams ?: return
        lp.width = targetWidth
        container.layoutParams = lp
    }
    
    /**
     * Остановка оверлей сервиса
     */
    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_OVERLAY -> updatePermissionStatus()
        }
    }
    
    /**
     * Обновляет все тексты UI согласно текущему языку
     */
    private fun updateUITexts() {
        // Кнопки разрешений обновляются в updatePermissionStatus()
        
        // Обновляем текст кнопки старта/стопа
        updateOverlayButtonState()
    }
}
