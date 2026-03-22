package com.egginc.autoclicker.utils

import android.content.Context
import android.os.SystemClock
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

/**
 * Класс конфигурации с координатами UI элементов
 * Базовые координаты для разрешения 1080x1920 (портрет)
 */
data class ClickerConfig(
    @SerializedName("base_resolution_width")
    var baseResolutionWidth: Int = 1440,
    
    @SerializedName("base_resolution_height")
    var baseResolutionHeight: Int = 3120,
    
    // БАЗОВЫЕ координаты для разрешения 1080x1920 (портрет)
    // Приложение само масштабирует под твой экран
    
    // Кнопка вызова куриц (точные координаты для 1440x3120)
    @SerializedName("chicken_button")
    var chickenButton: Point = Point(720, 2925),
    
    // Красная зона индикатора (полоса под цифрами вверху экрана)
    @SerializedName("red_zone_area")
    var redZoneArea: RectArea = RectArea(400, 200, 680, 240),
    
    // Слоты бустов (4 слота) - пока не используем
    @SerializedName("boost_slots")
    var boostSlots: List<Point> = listOf(
        Point(200, 1700),
        Point(400, 1700),
        Point(600, 1700),
        Point(800, 1700)
    ),
    
    // Кнопка активации буста - пока не используем
    @SerializedName("activate_boost_button")
    var activateBoostButton: Point = Point(540, 1200),
    
    // Область для поиска подарков (коричневая коробка вверху справа)
    // Для 1440x3120: X=1360, Y=450 (измерено через режим разработчика)
    @SerializedName("gift_area")
    var giftArea: RectArea = RectArea(750, 250, 1050, 350),
    
    // Координата для сбора подарка (точные координаты для 1440x3120: X=1360, Y=450)
    @SerializedName("gift_tap_location")
    var giftTapLocation: Point = Point(1360, 450),
    
    // Настройки таймеров куриц (настрой под свою ферму!)
    @SerializedName("chicken_swipe_duration_ms")
    var chickenSwipeDurationMs: Long = 4000, // 4 секунды удержания кнопки
    
    @SerializedName("chicken_rest_duration_ms")
    var chickenRestDurationMs: Long = 8000, // 8 секунд отдыха между удержаниями
    
    // Умный режим куриц: отслеживание красной полосы
    @SerializedName("smart_chicken_mode")
    var smartChickenMode: Boolean = false,
    
    // Координаты для проверки красной полосы индикатора (индикатор вверху экрана)
    // Точный красный пиксель найден: 655, 355 (RGB 240,13,13)
    @SerializedName("red_indicator_check_point")
    var redIndicatorCheckPoint: Point = Point(655, 355),
    
    // Время отката после обнаружения красной полосы (мс)
    @SerializedName("red_indicator_cooldown_ms")
    var redIndicatorCooldownMs: Long = 10000, // 10 секунд
    
    // Режим подарка: true = CV, false = таймер
    @SerializedName("use_cv_for_gift")
    var useCVForGift: Boolean = false,
    
    @SerializedName("chicken_timer_ms")
    var chickenTimerMs: Long = 200,
    
    @SerializedName("boost_check_interval_sec")
    var boostCheckIntervalSec: Int = 30,
    
    // Проверка подарка раз в минуту (60000 мс)
    @SerializedName("gift_check_interval_ms")
    var giftCheckIntervalMs: Long = 60000,
    
    // Пороги для CV
    @SerializedName("red_color_threshold")
    var redColorThreshold: Double = 0.7,
    
    @SerializedName("template_matching_threshold")
    var templateMatchingThreshold: Double = 0.8,

    // Нормализованный допуск стабильности для gift CV (доля от короткой стороны экрана)
    @SerializedName("gift_cv_stability_ratio")
    var giftCvStabilityRatio: Double = 0.0486, // 70px на базовой короткой стороне 1440
    
    // ===== АВТО-ФИЧИ (BETA) =====
    
    // Авто-исследования
    @SerializedName("auto_research_enabled")
    var autoResearchEnabled: Boolean = false,
    
    // Интервал проверки авто-исследований (секунды, 30-300, по умолчанию 90)
    @SerializedName("auto_research_interval_sec")
    var autoResearchIntervalSec: Int = 90,
    
    // Количество свайпов вверх при открытии лаборатории (по умолчанию 26)
    @SerializedName("research_swipes_up")
    var researchSwipesUp: Int = 26,
    
    // Количество свайпов вниз при сканировании (по умолчанию 12)
    @SerializedName("research_swipes_down")
    var researchSwipesDown: Int = 12,

    // Строгий шаблонный фильтр для кнопок исследований
    @SerializedName("research_template_strict_filter")
    var researchTemplateStrictFilter: Boolean = false,

    // Быстрый режим покупки исследований (меньше паузы между барражами)
    @SerializedName("research_fast_mode")
    var researchFastMode: Boolean = false,
    
    // ===== АВТО-БУСТ 2x ДЕНЬГИ =====
    // Требуется отключить рекламу в настройках игры!
    @SerializedName("auto_boost_2x_enabled")
    var autoBoost2xEnabled: Boolean = false,
    
    // Интервал повторения в минутах (1-60, по умолчанию 55)
    @SerializedName("auto_boost_2x_interval_min")
    var autoBoost2xIntervalMin: Int = 55,
    
    // Авто-ловля дронов
    @SerializedName("auto_drones_enabled")
    var autoDronesEnabled: Boolean = false,
    
    // ===== ЦВЕТА ДЛЯ РАСПОЗНАВАНИЯ =====
    // Погрешность цвета (±20 по каждому каналу)
    @SerializedName("color_tolerance")
    var colorTolerance: Int = 20,
    
    // Красный индикатор RGB(239, 13, 14)
    @SerializedName("color_red_indicator")
    var colorRedIndicator: RGBColor = RGBColor(239, 13, 14),
    
    // Коробка подарка RGB(86, 78, 41)
    @SerializedName("color_gift_box")
    var colorGiftBox: RGBColor = RGBColor(86, 78, 41),
    
    // Зелёная кнопка улучшения RGB(25, 172, 0)
    @SerializedName("color_research_green")
    var colorResearchGreen: RGBColor = RGBColor(25, 172, 0),
    
    // Неактивное улучшение (серое) RGB(128, 128, 128)
    @SerializedName("color_research_gray")
    var colorResearchGray: RGBColor = RGBColor(128, 128, 128),
    
    // Доступное но недоступное улучшение RGB(186, 230, 179)
    @SerializedName("color_research_light_green")
    var colorResearchLightGreen: RGBColor = RGBColor(186, 230, 179),
    
    // Координата для подтверждения сбора подарка (синяя кнопка)
    @SerializedName("gift_confirm_button")
    var giftConfirmButton: Point = Point(725, 1825),
    
    // ===== КООРДИНАТЫ ЛАБОРАТОРИИ =====
    
    // Кнопка входа в лабораторию (иконка пробирки внизу)
    @SerializedName("research_menu_button")
    var researchMenuButton: Point = Point(120, 2625),
    
    // Кнопка закрытия лаборатории (красный крестик)
    @SerializedName("research_close_button")
    var researchCloseButton: Point = Point(1350, 450),
    
    // Область сканирования зелёных кнопок RESEARCH (правая часть списка)
    @SerializedName("research_scan_area")
    var researchScanArea: RectArea = RectArea(1050, 600, 1400, 2400),
    
    // Интервал проверки лаборатории (мс)
    @SerializedName("research_check_interval_ms")
    var researchCheckIntervalMs: Long = 30000, // 30 секунд
    
    // Порог зелёности для детекции кнопки (0-255, чем ниже - тем чувствительнее)
    @SerializedName("research_green_threshold")
    var researchGreenThreshold: Int = 150,

    // Минимальные размеры кнопки исследований (доли от экрана)
    @SerializedName("research_min_button_width_ratio")
    var researchMinButtonWidthRatio: Double = 0.0486, // 70/1440

    @SerializedName("research_min_button_height_ratio")
    var researchMinButtonHeightRatio: Double = 0.0080, // 25/3120

    // Минимальная дистанция по Y для дедупликации кнопок (доля от высоты экрана)
    @SerializedName("research_button_dedup_y_ratio")
    var researchButtonDedupYRatio: Double = 0.0160, // 50/3120
    
    // Язык приложения ("ru", "en", "system")
    @SerializedName("app_language")
    var appLanguage: String = "system",

    // Режим CV-детекции подарка: "lite" или "accurate"
    @SerializedName("cv_detection_mode")
    var cvDetectionMode: String = ConfigManager.CV_MODE_LITE,
    
    // ===== НАСТРОЙКИ АВТО-БУСТОВ =====
    // Кнопка открытия меню бустов (иконка ракеты внизу)
    @SerializedName("boost_menu_button")
    var boostMenuButton: Point = Point(330, 2605),
    
    // Кнопка закрытия меню бустов (красный крестик)
    @SerializedName("boost_close_button")
    var boostCloseButton: Point = Point(1350, 450),
    
    // ===== КАСТОМИЗАЦИЯ БАЗЫ (для режима дронов) =====
    // Вход в кастомизацию фиксирует экран (нельзя двигать) и отдаляет вид
    
    // Кнопка открытия меню (иконка здания)
    @SerializedName("customize_menu_button")
    var customizeMenuButton: Point = Point(760, 2600),
    
    // Первый клик после анимации (в меню)
    @SerializedName("customize_first_click")
    var customizeFirstClick: Point = Point(1105, 1485),
    
    // Второй клик после анимации (вход в кастомизацию)
    @SerializedName("customize_second_click")
    var customizeSecondClick: Point = Point(380, 1400),
    
    // Координаты свайпа для ловли дронов (в кастомизации)
    // Начало: левый верхний угол игровой области
    @SerializedName("drone_swipe_start")
    var droneSwipeStart: Point = Point(10, 605),
    
    // Конец: правый нижний угол игровой области
    @SerializedName("drone_swipe_end")
    var droneSwipeEnd: Point = Point(1400, 2640),

    // Нормализованные координаты точек (0..1) для переносимости между экранами.
    // Ключи см. в ConfigManager.Companion.
    @SerializedName("normalized_points")
    var normalizedPoints: MutableMap<String, NormalizedPoint> = mutableMapOf()
)

data class Point(
    @SerializedName("x")
    var x: Int,
    
    @SerializedName("y")
    var y: Int
)

data class RectArea(
    @SerializedName("left")
    var left: Int,
    
    @SerializedName("top")
    var top: Int,
    
    @SerializedName("right")
    var right: Int,
    
    @SerializedName("bottom")
    var bottom: Int
)

data class RGBColor(
    @SerializedName("r")
    var r: Int,
    
    @SerializedName("g")
    var g: Int,
    
    @SerializedName("b")
    var b: Int
)

data class NormalizedPoint(
    @SerializedName("x")
    var x: Double,

    @SerializedName("y")
    var y: Double
)

class ConfigManager(context: Context) {
    companion object {
        const val CV_MODE_LITE = "lite"
        const val CV_MODE_ACCURATE = "accurate"

        const val KEY_CHICKEN_BUTTON = "chicken_button"
        const val KEY_GIFT_TAP = "gift_tap_location"
        const val KEY_RED_INDICATOR = "red_indicator_check_point"
        const val KEY_GIFT_CONFIRM = "gift_confirm_button"
        const val KEY_RESEARCH_MENU = "research_menu_button"
        const val KEY_RESEARCH_CLOSE = "research_close_button"
        const val KEY_BOOST_MENU = "boost_menu_button"
        const val KEY_BOOST_CLOSE = "boost_close_button"
        const val KEY_CUSTOMIZE_MENU = "customize_menu_button"
        const val KEY_CUSTOMIZE_CLICK1 = "customize_first_click"
        const val KEY_CUSTOMIZE_CLICK2 = "customize_second_click"
        const val KEY_DRONE_SWIPE_START = "drone_swipe_start"
        const val KEY_DRONE_SWIPE_END = "drone_swipe_end"

        private val POINT_KEYS = listOf(
            KEY_CHICKEN_BUTTON,
            KEY_GIFT_TAP,
            KEY_RED_INDICATOR,
            KEY_GIFT_CONFIRM,
            KEY_RESEARCH_MENU,
            KEY_RESEARCH_CLOSE,
            KEY_BOOST_MENU,
            KEY_BOOST_CLOSE,
            KEY_CUSTOMIZE_MENU,
            KEY_CUSTOMIZE_CLICK1,
            KEY_CUSTOMIZE_CLICK2,
            KEY_DRONE_SWIPE_START,
            KEY_DRONE_SWIPE_END
        )
    }
    
    private val gson = Gson()
    private val configFile = File(context.filesDir, "clicker_config.json")
    private val cacheLock = Any()
    @Volatile
    private var cachedConfig: ClickerConfig? = null
    @Volatile
    private var cachedFileMtime: Long = Long.MIN_VALUE
    @Volatile
    private var cachedAtElapsedMs: Long = 0L
    
    fun loadConfig(): ClickerConfig {
        val fileMtime = if (configFile.exists()) configFile.lastModified() else Long.MIN_VALUE
        val nowElapsed = SystemClock.elapsedRealtime()
        synchronized(cacheLock) {
            val cacheAlive = nowElapsed - cachedAtElapsedMs <= 750L
            val cacheValidForFile = cachedFileMtime == fileMtime
            if (cacheAlive && cacheValidForFile && cachedConfig != null) {
                return cachedConfig!!
            }
        }

        return try {
            val config = if (configFile.exists()) {
                val json = configFile.readText()
                gson.fromJson(json, ClickerConfig::class.java)
            } else {
                // Создаем дефолтную конфигурацию
                val defaultConfig = ClickerConfig()
                saveConfig(defaultConfig)
                defaultConfig
            }
            // Fix for Gson not using Kotlin default values
            if (config.appLanguage.isEmpty()) {
                config.appLanguage = "system"
            }
            if (config.cvDetectionMode != CV_MODE_LITE && config.cvDetectionMode != CV_MODE_ACCURATE) {
                config.cvDetectionMode = CV_MODE_LITE
            }
            config.giftCvStabilityRatio = config.giftCvStabilityRatio.coerceIn(0.01, 0.20)
            config.researchMinButtonWidthRatio = config.researchMinButtonWidthRatio.coerceIn(0.01, 0.20)
            config.researchMinButtonHeightRatio = config.researchMinButtonHeightRatio.coerceIn(0.003, 0.08)
            config.researchButtonDedupYRatio = config.researchButtonDedupYRatio.coerceIn(0.003, 0.10)
            migrateAndClampPoints(config, preferNormalized = true)
            synchronized(cacheLock) {
                cachedConfig = config
                cachedFileMtime = if (configFile.exists()) configFile.lastModified() else Long.MIN_VALUE
                cachedAtElapsedMs = SystemClock.elapsedRealtime()
            }
            config
        } catch (e: Exception) {
            e.printStackTrace()
            ClickerConfig()
        }
    }
    
    fun saveConfig(config: ClickerConfig) {
        try {
            migrateAndClampPoints(config, preferNormalized = false)
            val json = gson.toJson(config)
            configFile.writeText(json)
            synchronized(cacheLock) {
                cachedConfig = config
                cachedFileMtime = if (configFile.exists()) configFile.lastModified() else Long.MIN_VALUE
                cachedAtElapsedMs = SystemClock.elapsedRealtime()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun migrateAndClampPoints(config: ClickerConfig, preferNormalized: Boolean) {
        val baseWidth = config.baseResolutionWidth.coerceAtLeast(1)
        val baseHeight = config.baseResolutionHeight.coerceAtLeast(1)

        val normalizedMap = config.normalizedPoints
        if (normalizedMap.isEmpty()) {
            POINT_KEYS.forEach { key ->
                normalizedMap[key] = ScreenMapper.toNormalized(getPointByKey(config, key), baseWidth, baseHeight)
            }
        } else if (!preferNormalized) {
            // При сохранении текущие Point считаем источником истины,
            // чтобы не откатывать только что отредактированные координаты.
            POINT_KEYS.forEach { key ->
                normalizedMap[key] = ScreenMapper.toNormalized(getPointByKey(config, key), baseWidth, baseHeight)
            }
        }

        POINT_KEYS.forEach { key ->
            val normalized = normalizedMap[key]
            val clampedNormalized = if (normalized != null) {
                NormalizedPoint(
                    x = normalized.x.coerceIn(0.0, 1.0),
                    y = normalized.y.coerceIn(0.0, 1.0)
                )
            } else {
                ScreenMapper.toNormalized(getPointByKey(config, key), baseWidth, baseHeight)
            }

            normalizedMap[key] = clampedNormalized
            setPointByKey(config, key, ScreenMapper.normalizedToBase(clampedNormalized, baseWidth, baseHeight))
        }
    }

    private fun getPointByKey(config: ClickerConfig, key: String): Point {
        return when (key) {
            KEY_CHICKEN_BUTTON -> config.chickenButton
            KEY_GIFT_TAP -> config.giftTapLocation
            KEY_RED_INDICATOR -> config.redIndicatorCheckPoint
            KEY_GIFT_CONFIRM -> config.giftConfirmButton
            KEY_RESEARCH_MENU -> config.researchMenuButton
            KEY_RESEARCH_CLOSE -> config.researchCloseButton
            KEY_BOOST_MENU -> config.boostMenuButton
            KEY_BOOST_CLOSE -> config.boostCloseButton
            KEY_CUSTOMIZE_MENU -> config.customizeMenuButton
            KEY_CUSTOMIZE_CLICK1 -> config.customizeFirstClick
            KEY_CUSTOMIZE_CLICK2 -> config.customizeSecondClick
            KEY_DRONE_SWIPE_START -> config.droneSwipeStart
            KEY_DRONE_SWIPE_END -> config.droneSwipeEnd
            else -> Point(0, 0)
        }
    }

    private fun setPointByKey(config: ClickerConfig, key: String, point: Point) {
        when (key) {
            KEY_CHICKEN_BUTTON -> config.chickenButton = point
            KEY_GIFT_TAP -> config.giftTapLocation = point
            KEY_RED_INDICATOR -> config.redIndicatorCheckPoint = point
            KEY_GIFT_CONFIRM -> config.giftConfirmButton = point
            KEY_RESEARCH_MENU -> config.researchMenuButton = point
            KEY_RESEARCH_CLOSE -> config.researchCloseButton = point
            KEY_BOOST_MENU -> config.boostMenuButton = point
            KEY_BOOST_CLOSE -> config.boostCloseButton = point
            KEY_CUSTOMIZE_MENU -> config.customizeMenuButton = point
            KEY_CUSTOMIZE_CLICK1 -> config.customizeFirstClick = point
            KEY_CUSTOMIZE_CLICK2 -> config.customizeSecondClick = point
            KEY_DRONE_SWIPE_START -> config.droneSwipeStart = point
            KEY_DRONE_SWIPE_END -> config.droneSwipeEnd = point
        }
    }
}
