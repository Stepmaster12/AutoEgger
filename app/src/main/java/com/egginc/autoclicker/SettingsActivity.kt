package com.egginc.autoclicker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.core.graphics.toColorInt
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import com.egginc.autoclicker.utils.ConfigManager
import com.egginc.autoclicker.utils.LocaleHelper
import com.egginc.autoclicker.utils.Point
import com.egginc.autoclicker.utils.StringManager

/**
 * Activity для настройки координат точек кликов
 */
@SuppressLint("SetTextI18n")
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
    }

    private enum class SettingsPreset { SAFE, BALANCE, AGGRESSIVE }

    private data class UiState(
        val autoResearch: Boolean,
        val autoBoost: Boolean,
        val autoDrones: Boolean,
        val researchInterval: Int,
        val boostInterval: Int,
        val swipesUp: Int,
        val swipesDown: Int,
        val researchStrictFilter: Boolean,
        val researchFastMode: Boolean,
        val giftIntervalSec: Int,
        val cvMode: String
    )

    private lateinit var configManager: ConfigManager
    
    // Авто-фичи
    private lateinit var cbAutoResearch: CheckBox
    private lateinit var cbAutoBoost2x: CheckBox
    private lateinit var cbAutoDrones: CheckBox
    private lateinit var tvAutoResearchDesc: TextView
    private lateinit var tvAutoBoostDesc: TextView
    private lateinit var tvAutoBoostPauseWarning: TextView
    private lateinit var tvAutoDronesDesc: TextView
    private lateinit var tvAutoDronesConflict: TextView
    private lateinit var btnPresetSafe: Button
    private lateinit var btnPresetBalance: Button
    private lateinit var btnPresetAggressive: Button
    private lateinit var spinnerCvMode: Spinner
    
    // Настройка интервала авто-буста 2x
    private lateinit var layoutBoost2xInterval: LinearLayout
    private lateinit var seekbarBoost2xInterval: SeekBar
    private lateinit var tvBoost2xIntervalLabel: TextView
    private lateinit var etBoost2xInterval: EditText
    
    // Настройка интервала авто-исследований
    private lateinit var layoutResearchInterval: LinearLayout
    private lateinit var seekbarResearchInterval: SeekBar
    private lateinit var tvResearchIntervalLabel: TextView
    private lateinit var tvTimerWarning: TextView
    private lateinit var tvGiftIntervalInfo: TextView
    
    // Настройка свайпов лаборатории
    private lateinit var layoutSwipeSettings: LinearLayout
    private lateinit var etSwipesUp: EditText
    private lateinit var etSwipesDown: EditText
    private lateinit var cbResearchStrictFilter: CheckBox
    private lateinit var cbResearchFastMode: CheckBox
    
    // EditText для точного ввода интервала авто-исследований
    private lateinit var etResearchInterval: EditText
    
    // Текущий интервал подарков (загружается из конфига)
    private var giftIntervalSec = 60

    private var initialUiState: UiState? = null
    private var hasUnsavedChanges = false
    
    override fun attachBaseContext(newBase: Context) {
        val context = LocaleHelper.setLocale(newBase)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        limitContentWidth()

        configManager = ConfigManager(this)

        initViews()
        initPoints()
        setupDirtyTracking()
        setupBackHandling()
        markCurrentStateAsSaved()
    }

    private fun limitContentWidth() {
        val container = findViewById<View>(R.id.settings_content_container) ?: return
        val maxWidthPx = (640 * resources.displayMetrics.density).toInt()
        val screenWidth = resources.displayMetrics.widthPixels
        val targetWidth = minOf(maxWidthPx, screenWidth - (16 * resources.displayMetrics.density).toInt()).coerceAtLeast(0)
        val lp = container.layoutParams ?: return
        lp.width = targetWidth
        container.layoutParams = lp
    }
    
    override fun onResume() {
        super.onResume()
        // Перезагружаем язык
        StringManager.loadLanguage(this)
        updateUITexts()
        
        // Перезагружаем интервал подарков (могли изменить в главном меню)
        val config = configManager.loadConfig()
        giftIntervalSec = (config.giftCheckIntervalMs / 1000).toInt().coerceIn(10, 600)
        tvGiftIntervalInfo.text = StringManager.format("current_gift_interval", giftIntervalSec)
        setCvModeSelection(config.cvDetectionMode)
        
        // Обновляем предупреждение с учётом нового интервала
        if (cbAutoResearch.isChecked) {
            val researchInterval = seekbarResearchInterval.progress.coerceIn(30, 300)
            updateTimerWarning(researchInterval)
        }
        
        markCurrentStateAsSaved()
    }
    
    private fun initViews() {
        // Авто-фичи
        cbAutoResearch = findViewById(R.id.cb_auto_research)
        cbAutoBoost2x = findViewById(R.id.cb_auto_boosts)
        cbAutoDrones = findViewById(R.id.cb_auto_drones)
        tvAutoResearchDesc = findViewById(R.id.tv_auto_research_desc)
        tvAutoBoostDesc = findViewById(R.id.tv_auto_boost_desc)
        tvAutoBoostPauseWarning = findViewById(R.id.tv_auto_boost_pause_warning)
        tvAutoDronesDesc = findViewById(R.id.tv_auto_drones_desc)
        tvAutoDronesConflict = findViewById(R.id.tv_auto_drones_conflict)
        btnPresetSafe = findViewById(R.id.btn_preset_safe)
        btnPresetBalance = findViewById(R.id.btn_preset_balance)
        btnPresetAggressive = findViewById(R.id.btn_preset_aggressive)
        spinnerCvMode = findViewById(R.id.spinner_cv_mode)
        
        // Настройка интервала авто-буста 2x
        layoutBoost2xInterval = findViewById(R.id.layout_boost2x_interval)
        seekbarBoost2xInterval = findViewById(R.id.seekbar_boost2x_interval)
        tvBoost2xIntervalLabel = findViewById(R.id.tv_boost2x_interval_label)
        etBoost2xInterval = findViewById(R.id.et_boost2x_interval)
        
        // Настройка интервала авто-исследований
        layoutResearchInterval = findViewById(R.id.layout_research_interval)
        seekbarResearchInterval = findViewById(R.id.seekbar_research_interval)
        tvResearchIntervalLabel = findViewById(R.id.tv_research_interval_label)
        tvTimerWarning = findViewById(R.id.tv_timer_warning)
        tvGiftIntervalInfo = findViewById(R.id.tv_gift_interval_info)
        etResearchInterval = findViewById(R.id.et_research_interval)
        
        // Настройка свайпов лаборатории
        layoutSwipeSettings = findViewById(R.id.layout_swipe_settings)
        etSwipesUp = findViewById(R.id.et_swipes_up)
        etSwipesDown = findViewById(R.id.et_swipes_down)
        cbResearchStrictFilter = findViewById(R.id.cb_research_strict_filter)
        cbResearchFastMode = findViewById(R.id.cb_research_fast_mode)
        cbResearchStrictFilter.setOnCheckedChangeListener { _, _ -> updateUnsavedChangesState() }
        cbResearchFastMode.setOnCheckedChangeListener { _, _ -> updateUnsavedChangesState() }
        
        // Обновляем тексты согласно текущему языку
        updateUITexts()
        
        setupResearchIntervalControls()
        setupBoost2xIntervalControls()
        setupPresets()
        setupCvModeSpinner()
        
        // Кнопка назад
        findViewById<Button>(R.id.btn_back).setOnClickListener {
            attemptCloseWithUnsavedCheck()
        }
        
        // Кнопка сохранить
        findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveSettings()
        }
        
        // Кнопка сбросить
        findViewById<Button>(R.id.btn_reset).setOnClickListener {
            showResetConfirmDialog()
        }
        
        // Кнопка информации об авто-бусте 2x
        findViewById<ImageButton>(R.id.btn_boost2x_info)?.setOnClickListener {
            showBoost2xInfoDialog()
        }
        
        // Кнопка переключения языка
        findViewById<Button>(R.id.btn_language)?.apply {
            text = LocaleHelper.getCurrentFlag(context)
            setOnClickListener {
                showLanguageDialog()
            }
        }
    }

    private fun initPoints() {
        val config = configManager.loadConfig()
        
        // Загружаем авто-фичи
        cbAutoResearch.isChecked = config.autoResearchEnabled
        cbAutoBoost2x.isChecked = config.autoBoost2xEnabled
        cbAutoDrones.isChecked = config.autoDronesEnabled
        setCvModeSelection(config.cvDetectionMode)
        updateAutoFeatureLabelColors()
        
        // Загружаем интервал авто-буста 2x (1-60 мин, по умолчанию 55)
        val boost2xIntervalMin = config.autoBoost2xIntervalMin.coerceIn(1, 60)
        etBoost2xInterval.setText(boost2xIntervalMin.toString())
        seekbarBoost2xInterval.progress = boost2xIntervalMin
        tvBoost2xIntervalLabel.text = getString(R.string.interval_min, boost2xIntervalMin)
        updateBoost2xIntervalUI(config.autoBoost2xEnabled, boost2xIntervalMin)
        
        // Загружаем настройки свайпов лаборатории
        etSwipesUp.setText(config.researchSwipesUp.toString())
        etSwipesDown.setText(config.researchSwipesDown.toString())
        cbResearchStrictFilter.isChecked = config.researchTemplateStrictFilter
        cbResearchFastMode.isChecked = config.researchFastMode
        
        // Загружаем интервал подарков (для проверки конфликтов)
        giftIntervalSec = (config.giftCheckIntervalMs / 1000).toInt().coerceIn(10, 600)
        tvGiftIntervalInfo.text = getString(R.string.current_gift_interval_full, giftIntervalSec)
        
        // Загружаем интервал (мин 30, макс 300, по умолчанию 90)
        val intervalSec = config.autoResearchIntervalSec.coerceIn(30, 300)
        etResearchInterval.setText(intervalSec.toString())
        seekbarResearchInterval.progress = intervalSec
        tvResearchIntervalLabel.text = getString(R.string.interval_sec, intervalSec)
        
        // Устанавливаем начальное предупреждение
        updateTimerWarning(intervalSec)
        updateResearchIntervalUI(config.autoResearchEnabled, intervalSec)
        updateAutoFeatureSectionsVisibility()
    }

    private fun setupPresets() {
        btnPresetSafe.setOnClickListener { applyPreset(SettingsPreset.SAFE) }
        btnPresetBalance.setOnClickListener { applyPreset(SettingsPreset.BALANCE) }
        btnPresetAggressive.setOnClickListener { applyPreset(SettingsPreset.AGGRESSIVE) }
    }

    private fun setupCvModeSpinner() {
        val items = listOf(
            getString(R.string.cv_mode_lite),
            getString(R.string.cv_mode_accurate)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCvMode.adapter = adapter
        spinnerCvMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateUnsavedChangesState()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun setCvModeSelection(mode: String) {
        val position = if (mode == ConfigManager.CV_MODE_ACCURATE) 1 else 0
        if (spinnerCvMode.selectedItemPosition != position) {
            spinnerCvMode.setSelection(position, false)
        }
    }

    private fun getSelectedCvMode(): String {
        return if (spinnerCvMode.selectedItemPosition == 1) {
            ConfigManager.CV_MODE_ACCURATE
        } else {
            ConfigManager.CV_MODE_LITE
        }
    }

    private fun applyPreset(preset: SettingsPreset) {
        val values = when (preset) {
            SettingsPreset.SAFE -> PresetValues(
                giftIntervalSec = 90,
                researchIntervalSec = 150
            )
            SettingsPreset.BALANCE -> PresetValues(
                giftIntervalSec = 60,
                researchIntervalSec = 90
            )
            SettingsPreset.AGGRESSIVE -> PresetValues(
                giftIntervalSec = 10,
                researchIntervalSec = 30
            )
        }

        giftIntervalSec = values.giftIntervalSec
        tvGiftIntervalInfo.text = getString(R.string.current_gift_interval_full, giftIntervalSec)

        etResearchInterval.setText(values.researchIntervalSec.toString())
        seekbarResearchInterval.progress = values.researchIntervalSec
        tvResearchIntervalLabel.text = getString(R.string.interval_sec_format, values.researchIntervalSec)

        updateTimerWarning(values.researchIntervalSec)
        updateUnsavedChangesState()

        val presetName = when (preset) {
            SettingsPreset.SAFE -> getString(R.string.preset_safe)
            SettingsPreset.BALANCE -> getString(R.string.preset_balance)
            SettingsPreset.AGGRESSIVE -> getString(R.string.preset_aggressive)
        }
        Toast.makeText(this, getString(R.string.preset_applied, presetName), Toast.LENGTH_SHORT).show()
    }

    private data class PresetValues(
        val giftIntervalSec: Int,
        val researchIntervalSec: Int
    )

    private fun setupDirtyTracking() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateUnsavedChangesState()
        }

        etResearchInterval.addTextChangedListener(watcher)
        etBoost2xInterval.addTextChangedListener(watcher)
        etSwipesUp.addTextChangedListener(watcher)
        etSwipesDown.addTextChangedListener(watcher)
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                attemptCloseWithUnsavedCheck()
            }
        })
    }

    private fun attemptCloseWithUnsavedCheck() {
        updateUnsavedChangesState()
        if (!hasUnsavedChanges) {
            finish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.unsaved_changes_title))
            .setMessage(getString(R.string.unsaved_changes_message))
            .setPositiveButton(getString(R.string.save_and_exit)) { _, _ ->
                saveSettings()
            }
            .setNeutralButton(getString(R.string.exit_without_saving)) { _, _ ->
                finish()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun captureUiState(): UiState {
        return UiState(
            autoResearch = cbAutoResearch.isChecked,
            autoBoost = cbAutoBoost2x.isChecked,
            autoDrones = cbAutoDrones.isChecked,
            researchInterval = etResearchInterval.text.toString().toIntOrNull() ?: seekbarResearchInterval.progress,
            boostInterval = etBoost2xInterval.text.toString().toIntOrNull() ?: seekbarBoost2xInterval.progress,
            swipesUp = etSwipesUp.text.toString().toIntOrNull() ?: 26,
            swipesDown = etSwipesDown.text.toString().toIntOrNull() ?: 12,
            researchStrictFilter = cbResearchStrictFilter.isChecked,
            researchFastMode = cbResearchFastMode.isChecked,
            giftIntervalSec = giftIntervalSec.coerceIn(10, 600),
            cvMode = getSelectedCvMode()
        )
    }

    private fun updateUnsavedChangesState() {
        hasUnsavedChanges = initialUiState?.let { it != captureUiState() } ?: false
    }

    private fun markCurrentStateAsSaved() {
        initialUiState = captureUiState()
        hasUnsavedChanges = false
    }


    private fun saveSettings(finishActivity: Boolean = true) {
        val config = configManager.loadConfig()
        
        // Сохраняем авто-фичи
        config.autoResearchEnabled = cbAutoResearch.isChecked
        config.autoBoost2xEnabled = cbAutoBoost2x.isChecked
        config.autoDronesEnabled = cbAutoDrones.isChecked
        config.cvDetectionMode = getSelectedCvMode()
        config.giftCheckIntervalMs = giftIntervalSec.coerceIn(10, 600) * 1000L
        
        // Сохраняем интервал авто-буста 2x (1-60 мин)
        val boost2xInterval = etBoost2xInterval.text.toString().toIntOrNull() 
            ?: seekbarBoost2xInterval.progress.coerceIn(1, 60)
        config.autoBoost2xIntervalMin = boost2xInterval.coerceIn(1, 60)
        
        // Сохраняем интервал авто-исследований (30-300 сек) - берём из EditText или SeekBar
        val researchInterval = etResearchInterval.text.toString().toIntOrNull() 
            ?: seekbarResearchInterval.progress.coerceIn(30, 300)
        config.autoResearchIntervalSec = researchInterval.coerceIn(30, 300)
        
        // Сохраняем настройки свайпов (5-50 вверх, 5-30 вниз)
        val swipesUp = etSwipesUp.text.toString().toIntOrNull() ?: 26
        val swipesDown = etSwipesDown.text.toString().toIntOrNull() ?: 12
        config.researchSwipesUp = swipesUp.coerceIn(5, 50)
        config.researchSwipesDown = swipesDown.coerceIn(5, 30)
        config.researchTemplateStrictFilter = cbResearchStrictFilter.isChecked
        config.researchFastMode = cbResearchFastMode.isChecked
        
        configManager.saveConfig(config)
        markCurrentStateAsSaved()
        
        if (finishActivity) {
            Toast.makeText(this, getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    /**
     * Настраивает контролы для интервала авто-буста 2x
     */
    private fun setupBoost2xIntervalControls() {
        // Обработка изменения чекбокса авто-буста 2x
        cbAutoBoost2x.setOnCheckedChangeListener { _, isChecked ->
            val currentInterval = seekbarBoost2xInterval.progress.coerceIn(1, 60)
            updateBoost2xIntervalUI(isChecked, currentInterval)
            updateAutoFeatureSectionsVisibility()
            updateAutoFeatureLabelColors()
            updateUnsavedChangesState()
        }
        
        // Обработка изменения SeekBar (1-60 мин)
        seekbarBoost2xInterval.max = 60
        seekbarBoost2xInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val intervalMin = progress.coerceIn(1, 60)
                tvBoost2xIntervalLabel.text = getString(R.string.interval_min_format, intervalMin)
                etBoost2xInterval.setText(intervalMin.toString())
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Обработка изменения EditText интервала
        etBoost2xInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = etBoost2xInterval.text.toString().toIntOrNull() ?: 55
                val clampedValue = value.coerceIn(1, 60)
                etBoost2xInterval.setText(clampedValue.toString())
                seekbarBoost2xInterval.progress = clampedValue
                tvBoost2xIntervalLabel.text = getString(R.string.interval_min_format, clampedValue)
            }
        }
    }
    
    private fun updateBoost2xIntervalUI(enabled: Boolean, intervalMin: Int) {
        // Включаем/выключаем блок настройки интервала
        layoutBoost2xInterval.isEnabled = enabled
        layoutBoost2xInterval.alpha = if (enabled) 1.0f else 0.5f
        seekbarBoost2xInterval.isEnabled = enabled
        etBoost2xInterval.isEnabled = enabled
        
        tvBoost2xIntervalLabel.text = getString(R.string.interval_min_format, intervalMin)
    }

    private fun setupResearchIntervalControls() {
        // Обработка изменения чекбокса авто-исследований
        cbAutoResearch.setOnCheckedChangeListener { _, isChecked ->
            val currentInterval = seekbarResearchInterval.progress
            updateResearchIntervalUI(isChecked, currentInterval)
            updateAutoFeatureSectionsVisibility()
            updateAutoFeatureLabelColors()
            updateUnsavedChangesState()
        }
        
        // Ручная настройка точек дронов отключена, блок всегда скрыт.
        cbAutoDrones.setOnCheckedChangeListener { _, _ ->
            updateAutoFeatureSectionsVisibility()
            updateAutoFeatureLabelColors()
            updateUnsavedChangesState()
        }
        
        // Обработка изменения SeekBar (30-300 сек)
        seekbarResearchInterval.max = 300
        seekbarResearchInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Минимальное значение 30 сек
                val intervalSec = progress.coerceIn(30, 300)
                tvResearchIntervalLabel.text = getString(R.string.interval_sec_format, intervalSec)
                etResearchInterval.setText(intervalSec.toString())
                
                // Проверяем конфликт с таймером подарков
                updateTimerWarning(intervalSec)
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Обработка изменения EditText интервала
        etResearchInterval.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = etResearchInterval.text.toString().toIntOrNull() ?: 90
                val clampedValue = value.coerceIn(30, 300)
                etResearchInterval.setText(clampedValue.toString())
                seekbarResearchInterval.progress = clampedValue
                tvResearchIntervalLabel.text = getString(R.string.interval_sec_format, clampedValue)
                updateTimerWarning(clampedValue)
            }
        }
    }
    
    private fun updateResearchIntervalUI(enabled: Boolean, intervalSec: Int) {
        // Включаем/выключаем блок настройки интервала
        layoutResearchInterval.isEnabled = enabled
        layoutResearchInterval.alpha = if (enabled) 1.0f else 0.5f
        seekbarResearchInterval.isEnabled = enabled
        
        // Включаем/выключаем блок настройки свайпов лаборатории
        layoutSwipeSettings.isEnabled = enabled
        layoutSwipeSettings.alpha = if (enabled) 1.0f else 0.5f
        etSwipesUp.isEnabled = enabled
        etSwipesDown.isEnabled = enabled
        cbResearchStrictFilter.isEnabled = enabled
        cbResearchFastMode.isEnabled = enabled
        
        tvResearchIntervalLabel.text = getString(R.string.interval_sec_format, intervalSec)
        
        // Показываем предупреждение только если включены авто-исследования
        if (enabled) {
            updateTimerWarning(intervalSec)
        } else {
            tvTimerWarning.visibility = View.GONE
        }
    }
    
    /**
     * Обновляет предупреждение о конфликте таймеров
     * Проверяет совпадение с интервалом подарков
     */
    private fun updateTimerWarning(researchIntervalSec: Int) {
        val hasConflict = hasTimerConflict(researchIntervalSec, giftIntervalSec)
        
        if (hasConflict) {
            tvTimerWarning.text = getString(R.string.timer_conflict_detailed, researchIntervalSec, giftIntervalSec)
            tvTimerWarning.visibility = View.VISIBLE
        } else {
            tvTimerWarning.visibility = View.GONE
        }
    }
    
    /**
     * Проверяет конфликт между таймерами
     * Возвращает true если интервалы совпадают или кратны друг другу
     */
    private fun hasTimerConflict(researchSec: Int, giftSec: Int): Boolean {
        // Слишком частые проверки (менее 30 сек) - всегда предупреждаем
        if (researchSec < 30) return true
        
        // Прямое совпадение
        if (researchSec == giftSec) return true
        
        // Кратность: если один интервал делится на другой без остатка
        // Например: gift=60, research=30 (60%30==0) - конфликт
        //           gift=60, research=90 (90%60==30) - нет конфликта
        //           gift=60, research=120 (120%60==0) - конфликт
        if (giftSec % researchSec == 0 || researchSec % giftSec == 0) return true
        
        // Проверяем близкие значения (разница менее 10 секунд)
        if (kotlin.math.abs(researchSec - giftSec) < 10) return true
        
        return false
    }
    
    /**
     * Показывает информационный диалог об Авто-бусте 2x
     */
    private fun showBoost2xInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_boost_title))
            .setMessage(getString(R.string.dialog_boost_message))
            .setPositiveButton(getString(R.string.dialog_understand), null)
            .show()
    }

    private fun updateAutoFeatureSectionsVisibility() {
        val researchEnabled = cbAutoResearch.isChecked
        val boostEnabled = cbAutoBoost2x.isChecked
        val dronesEnabled = cbAutoDrones.isChecked

        tvAutoResearchDesc.visibility = if (researchEnabled) View.VISIBLE else View.GONE
        layoutResearchInterval.visibility = if (researchEnabled) View.VISIBLE else View.GONE
        layoutSwipeSettings.visibility = if (researchEnabled) View.VISIBLE else View.GONE
        if (researchEnabled) {
            updateTimerWarning(seekbarResearchInterval.progress.coerceIn(30, 300))
        } else {
            tvTimerWarning.visibility = View.GONE
        }

        tvAutoBoostDesc.visibility = if (boostEnabled) View.VISIBLE else View.GONE
        tvAutoBoostPauseWarning.visibility = if (boostEnabled) View.VISIBLE else View.GONE
        layoutBoost2xInterval.visibility = if (boostEnabled) View.VISIBLE else View.GONE

        tvAutoDronesDesc.visibility = if (dronesEnabled) View.VISIBLE else View.GONE
        tvAutoDronesConflict.visibility = if (dronesEnabled) View.VISIBLE else View.GONE
    }
    
    /**
     * Показывает диалог выбора языка
     */
    private fun showLanguageDialog() {
        val otherLanguage = if (LocaleHelper.isRussian(this)) LocaleHelper.LANGUAGE_ENGLISH else LocaleHelper.LANGUAGE_RUSSIAN
        val languageName = StringManager.getLanguageName(otherLanguage)
        
        AlertDialog.Builder(this)
            .setTitle(StringManager.get("language"))
            .setMessage(StringManager.format("language_switch_message", languageName))
            .setPositiveButton(languageName) { _, _ ->
                // Сохраняем новый язык
                LocaleHelper.changeLanguage(this, otherLanguage)
                StringManager.loadLanguage(this)

                // Мягкий перезапуск task без принудительного Runtime.exit().
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                    startActivity(launchIntent)
                    finishAffinity()
                } else {
                    recreate()
                }
            }
            .setNegativeButton(StringManager.get("dialog_cancel"), null)
            .show()
    }

    private fun showResetConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(StringManager.get("dialog_reset_title"))
            .setMessage(getString(R.string.dialog_reset_details))
            .setPositiveButton(StringManager.get("dialog_reset_confirm")) { _, _ ->
                resetToDefaults()
            }
            .setNegativeButton(StringManager.get("dialog_cancel"), null)
            .show()
    }

    private fun resetToDefaults() {
        // Сбрасываем авто-фичи
        cbAutoResearch.isChecked = false
        cbAutoBoost2x.isChecked = false
        cbAutoDrones.isChecked = false
        
        // Сбрасываем интервал авто-буста 2x (55 мин)
        seekbarBoost2xInterval.progress = 55
        etBoost2xInterval.setText("55")
        tvBoost2xIntervalLabel.text = getString(R.string.interval_min, 55)
        
        // Сбрасываем интервал авто-исследований (90 сек)
        seekbarResearchInterval.progress = 90
        etResearchInterval.setText("90")
        tvResearchIntervalLabel.text = getString(R.string.interval_sec, 90)
        
        // Сбрасываем настройки свайпов лаборатории
        etSwipesUp.setText("26")
        etSwipesDown.setText("12")
        cbResearchStrictFilter.isChecked = false
        cbResearchFastMode.isChecked = false
        
        // Обновляем UI (делаем серым блок настроек)
        updateResearchIntervalUI(false, 90)
        
        // Сбрасываем координаты дронов в конфиге
        val config = configManager.loadConfig()
        resetDronePointsToDefaults(config)
        configManager.saveConfig(config)
        
        // СОХРАНЯЕМ сброшенные значения в конфиг
        saveSettings(finishActivity = false)
        updateUnsavedChangesState()
        
        Toast.makeText(this, getString(R.string.toast_all_stopped), Toast.LENGTH_SHORT).show()
    }
    
    private fun resetDronePointsToDefaults(config: com.egginc.autoclicker.utils.ClickerConfig) {
        config.customizeMenuButton = Point(760, 2600)
        config.customizeFirstClick = Point(1105, 1485)
        config.customizeSecondClick = Point(380, 1400)
        config.droneSwipeStart = Point(10, 605)
        config.droneSwipeEnd = Point(1400, 2640)
    }

    private fun updateAutoFeatureLabelColors() {
        val enabledColor = ContextCompat.getColor(this, android.R.color.white)
        val disabledColor = ContextCompat.getColor(this, android.R.color.darker_gray)
        val enabledAccent = "#FF9800".toColorInt()
        cbAutoResearch.setTextColor(if (cbAutoResearch.isChecked) enabledColor else disabledColor)
        cbAutoBoost2x.setTextColor(if (cbAutoBoost2x.isChecked) enabledColor else disabledColor)
        cbAutoDrones.setTextColor(if (cbAutoDrones.isChecked) enabledColor else disabledColor)
        tvAutoBoostDesc.setTextColor(if (cbAutoBoost2x.isChecked) enabledAccent else disabledColor)
        tvAutoBoostPauseWarning.setTextColor(disabledColor)
        tvAutoDronesDesc.setTextColor(if (cbAutoDrones.isChecked) enabledAccent else disabledColor)
        tvAutoDronesConflict.setTextColor(disabledColor)
        tvAutoBoostDesc.alpha = if (cbAutoBoost2x.isChecked) 1.0f else 0.65f
        tvAutoBoostPauseWarning.alpha = if (cbAutoBoost2x.isChecked) 1.0f else 0.65f
        tvAutoDronesDesc.alpha = if (cbAutoDrones.isChecked) 1.0f else 0.65f
        tvAutoDronesConflict.alpha = if (cbAutoDrones.isChecked) 1.0f else 0.65f
    }

    /**
     * Обновляет все тексты UI согласно текущему языку
     */
    private fun updateUITexts() {
        // Заголовок
        title = StringManager.get("settings_title")
        
        // Кнопки
        findViewById<Button>(R.id.btn_back)?.text = StringManager.get("exit")
        findViewById<Button>(R.id.btn_save)?.text = StringManager.get("save")
        findViewById<Button>(R.id.btn_reset)?.text = StringManager.get("reset_settings")
        findViewById<Button>(R.id.btn_language)?.text = StringManager.getCurrentFlag()
        
        // CheckBox'ы авто-фич
        cbAutoResearch.text = StringManager.get("auto_research")
        cbAutoBoost2x.text = StringManager.get("auto_boost2x")
        cbAutoDrones.text = StringManager.get("auto_drones")
        cbResearchFastMode.text = StringManager.get("research_fast_mode")
        
    }
}


