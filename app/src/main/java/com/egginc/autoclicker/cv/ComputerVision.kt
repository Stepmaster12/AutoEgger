package com.egginc.autoclicker.cv

import android.util.Log
import com.egginc.autoclicker.utils.ClickerConfig
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Класс для компьютерного зрения с использованием OpenCV
 */
class ComputerVision {

    data class GiftDetectionResult(
        val found: Boolean,
        val x: Int = 0,
        val y: Int = 0,
        val score: Float = 0f
    )
    
    companion object {
        private const val TAG = "ComputerVision"
        private val BASE_GIFT_CHECK_POINTS = listOf(
            Pair(0, 0), Pair(-15, -15), Pair(15, -15), Pair(-15, 15), Pair(15, 15),
            Pair(0, -25), Pair(0, 25), Pair(-25, 0), Pair(25, 0),
            Pair(-10, 0), Pair(10, 0), Pair(0, -10), Pair(0, 10)
        )
        
    }
    
    /**
     * Проверяет наличие подарочной коробки Egg Inc
     * Комбинирует точечную проверку и анализ цветовой площади
     * 
     * @param bitmap Скриншот экрана
     * @param centerX Центр коробки по X
     * @param centerY Центр коробки по Y
     * @return true если коробка обнаружена
     */
    fun detectGiftBox(
        bitmap: android.graphics.Bitmap,
        config: ClickerConfig,
        centerX: Int,
        centerY: Int,
        templateBitmap: android.graphics.Bitmap? = null,
        cvMode: String = "lite"
    ): GiftDetectionResult {
        return try {
            val accurateMode = cvMode.equals("accurate", ignoreCase = true)
            // ROI: ожидаем верхний правый сектор, но даем люфт вокруг ожидаемой точки.
            val roi = buildSearchRoi(bitmap.width, bitmap.height, centerX, centerY)
            val allowedDx = (bitmap.width * if (accurateMode) 0.34f else 0.30f).toInt().coerceIn(120, 560)
            val allowedDy = (bitmap.height * if (accurateMode) 0.24f else 0.20f).toInt().coerceIn(120, 700)
            var bestX = 0
            var bestY = 0
            var bestCheapScore = 0f
            var bestPointScore = 0f
            var bestAreaRatio = 0f
            val shortEdge = min(bitmap.width, bitmap.height).coerceAtLeast(1)
            val scaleFactor = (shortEdge / 1440f).coerceIn(0.5f, 2.2f)
            val checkPoints = buildScaledGiftCheckPoints(scaleFactor)

            // Чем меньше экран, тем меньше step для точности; чем больше — тем крупнее step ради CPU.
            val step = if (accurateMode) {
                (min(bitmap.width, bitmap.height) / 150).coerceIn(4, 10)
            } else {
                (min(bitmap.width, bitmap.height) / 115).coerceIn(6, 14)
            }
            val pointMin = if (accurateMode) 0.24f else 0.30f
            val areaMin = if (accurateMode) 0.14f else 0.18f
            val cheapThreshold = if (accurateMode) 0.95f else 1.12f
            for (y in roi.top until roi.bottom step step) {
                for (x in roi.left until roi.right step step) {
                    if (abs(x - centerX) > allowedDx || abs(y - centerY) > allowedDy) continue
                    val pointScore = detectGiftByPointsScore(bitmap, x, y, checkPoints, config.colorTolerance)
                    if (pointScore < pointMin) continue

                    val boxSize = max(64, min(bitmap.width, bitmap.height) / 15)
                    val left = max(0, x - boxSize / 2)
                    val top = max(0, y - boxSize / 2)
                    val right = min(bitmap.width, x + boxSize / 2)
                    val bottom = min(bitmap.height, y + boxSize / 2)
                    val areaRatio = detectGiftAreaRatio(bitmap, left, top, right, bottom, if (accurateMode) 2 else 3)
                    if (areaRatio < areaMin) continue
                    val cheapScore = pointScore * 1.6f + areaRatio * 2.25f
                    if (cheapScore > bestCheapScore) {
                        bestCheapScore = cheapScore
                        bestPointScore = pointScore
                        bestAreaRatio = areaRatio
                        bestX = x
                        bestY = y
                    }
                }
            }

            if (bestCheapScore < cheapThreshold) {
                Log.d(
                    TAG,
                    "Gift detect mode=$cvMode ROI=(${roi.left},${roi.top})-(${roi.right},${roi.bottom}), " +
                        "gate=±($allowedDx,$allowedDy), bestCheap=${"%.2f".format(bestCheapScore)}, accepted=false"
                )
                return GiftDetectionResult(false)
            }

            // Тяжелую template-проверку считаем только для лучшего кандидата.
            val templateScore = templateBitmap?.let {
                if (accurateMode) {
                    // Для точного режима проверяем локальный 3x3 neighborhood и берём лучший матч.
                    val templateOffset = (12f * scaleFactor).toInt().coerceIn(7, 28)
                    val offsets = listOf(
                        Pair(0, 0), Pair(-12, 0), Pair(12, 0),
                        Pair(0, -12), Pair(0, 12), Pair(-12, -12),
                        Pair(12, -12), Pair(-12, 12), Pair(12, 12)
                    ).map { (dx, dy) ->
                        Pair(
                            (dx / 12f * templateOffset).toInt(),
                            (dy / 12f * templateOffset).toInt()
                        )
                    }
                    offsets.maxOf { (dx, dy) ->
                        compareTemplateAtPoint(bitmap, it, bestX + dx, bestY + dy, 28)
                    }
                } else {
                    compareTemplateAtPoint(bitmap, it, bestX, bestY, 24)
                }
            } ?: 0.0f
            if (templateBitmap != null) {
                val templateMin = if (accurateMode) 0.50f else 0.57f
                if (templateScore < templateMin) {
                    Log.d(
                        TAG,
                        "Gift detect reject: weak template score=${"%.2f".format(templateScore)} < $templateMin " +
                            "(mode=$cvMode, best=($bestX,$bestY), point=${"%.2f".format(bestPointScore)}, area=${"%.2f".format(bestAreaRatio)})"
                    )
                    return GiftDetectionResult(false)
                }
            }
            val finalScore = bestCheapScore + templateScore * (if (accurateMode) 1.25f else 1.15f)
            val thresholdShift = ((config.templateMatchingThreshold - 0.8).toFloat() * 0.6f).coerceIn(-0.2f, 0.2f)
            val accepted = finalScore >= (if (accurateMode) 1.75f else 1.95f) + thresholdShift
            val result = if (accepted) {
                GiftDetectionResult(found = true, x = bestX, y = bestY, score = finalScore)
            } else {
                GiftDetectionResult(false)
            }
            Log.d(
                TAG,
                "Gift detect ROI=(${roi.left},${roi.top})-(${roi.right},${roi.bottom}), " +
                    "gate=±($allowedDx,$allowedDy), " +
                    "mode=$cvMode, " +
                    "best=($bestX,$bestY) point=${"%.2f".format(bestPointScore)}, area=${"%.2f".format(bestAreaRatio)}, cheap=${"%.2f".format(bestCheapScore)}, " +
                    "tmpl=${"%.2f".format(templateScore)}, score=${"%.2f".format(finalScore)}, accepted=$accepted"
            )
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error in detectGiftBox", e)
            GiftDetectionResult(false)
        }
    }

    // Кэш уменьшенных шаблонов: снижает CPU/GC в горячем пути template matching.
    private val scaledTemplateCache = LinkedHashMap<Int, android.graphics.Bitmap>(8, 0.75f, true)
    private val scaledTemplateCacheLock = Any()
    private val scaledCheckPointsCache = HashMap<Int, List<Pair<Int, Int>>>()
    private val scaledCheckPointsLock = Any()
    
    /**
     * Точечная детекция коробки
     */
    private fun detectGiftByPointsScore(
        bitmap: android.graphics.Bitmap,
        centerX: Int,
        centerY: Int,
        checkPoints: List<Pair<Int, Int>>,
        tolerance: Int
    ): Float {
        var matchedPoints = 0
        
        for ((dx, dy) in checkPoints) {
            val x = centerX + dx
            val y = centerY + dy
            
            if (x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                if (isGiftBoxColor(pixel, tolerance)) {
                    matchedPoints++
                }
            }
        }

        return matchedPoints.toFloat() / checkPoints.size.toFloat()
    }
    
    /**
     * Детекция по цветовой площади
     * Считает процент коричневых пикселей в области
     */
    private fun detectGiftAreaRatio(
        bitmap: android.graphics.Bitmap, 
        left: Int, top: Int, right: Int, bottom: Int
        , sampleStep: Int = 3
    ): Float {
        if (right <= left || bottom <= top) return 0f
        var brownPixels = 0
        var totalPixels = 0

        val width = right - left
        val height = bottom - top
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, left, top, width, height)

        val step = sampleStep.coerceIn(2, 4)
        for (y in 0 until height step step) {
            val rowOffset = y * width
            for (x in 0 until width step step) {
                totalPixels++
                if (isGiftBoxColor(pixels[rowOffset + x])) {
                    brownPixels++
                }
            }
        }
        
        val ratio = if (totalPixels > 0) brownPixels.toFloat() / totalPixels else 0f
        
        return ratio
    }
    
    /**
     * Проверяет, является ли пиксель цветом коробки подарка
     * Точный цвет: RGB(86, 78, 41) - тёмно-коричневый
     * 
     * СТРОГИЙ диапазон для избежания ложных срабатываний
     */
    private fun isGiftBoxColor(pixel: Int, tolerance: Int = 20): Boolean {
        val red = android.graphics.Color.red(pixel)
        val green = android.graphics.Color.green(pixel)
        val blue = android.graphics.Color.blue(pixel)
        
        // Основной цвет коробки: RGB(86, 78, 41) - тёмно-коричневый
        // УЖЕСТОЧЕНО: tolerance снижен с 40 до 20
        val isExactColor = kotlin.math.abs(red - 86) <= tolerance &&
                           kotlin.math.abs(green - 78) <= tolerance &&
                           kotlin.math.abs(blue - 41) <= tolerance
        
        // Коричневый вариант - УЖЕСТОЧЕНЫ диапазоны
        // Характерно для коричневого: R и G близки, B значительно ниже
        val isBrown = red in 61..149 &&             // Увеличен min с 50 до 60
                      green in 51..139 &&           // Увеличен min с 40 до 50
                      blue > 20 && blue < 90 &&     // Уменьшен max с 120 до 90
                      kotlin.math.abs(red - green) < 30 &&  // УЖЕСТОЧЕНО: с 40 до 30
                      red > blue + 20 &&            // УЖЕСТОЧЕНО: с 10 до 20
                      green > blue + 15             // УЖЕСТОЧЕНО: с 10 до 15
        
        // Золотисто-коричневый - УЖЕСТОЧЕНЫ диапазоны
        val isGoldenBrown = red in 111..199 &&          // Увеличен min с 100
                            green in 91..179 &&         // Увеличен min с 80
                            blue > 30 && blue < 85 &&     // Уменьшен max с 100
                            red >= green &&
                            green > blue + 25             // УЖЕСТОЧЕНО: с 20 до 25
        
        return isExactColor || isBrown || isGoldenBrown
    }

    private fun buildScaledGiftCheckPoints(scaleFactor: Float): List<Pair<Int, Int>> {
        val key = (scaleFactor * 100f).toInt().coerceIn(50, 220)
        synchronized(scaledCheckPointsLock) {
            val cached = scaledCheckPointsCache[key]
            if (cached != null) return cached

            val result = BASE_GIFT_CHECK_POINTS.map { (dx, dy) ->
                Pair(
                    (dx * scaleFactor).toInt(),
                    (dy * scaleFactor).toInt()
                )
            }.distinct()
            scaledCheckPointsCache[key] = result
            return result
        }
    }

    private data class SearchRoi(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun buildSearchRoi(width: Int, height: Int, expectedX: Int, expectedY: Int): SearchRoi {
        val roiWidth = (width * 0.46f).toInt()
        val roiHeight = (height * 0.30f).toInt()

        val expectedLeft = (expectedX - roiWidth / 2).coerceIn(0, max(0, width - roiWidth))
        val expectedTop = (expectedY - roiHeight / 2).coerceIn(0, max(0, height - roiHeight))
        val expectedRight = min(width, expectedLeft + roiWidth)
        val expectedBottom = min(height, expectedTop + roiHeight)

        // Страховочная зона: только верхний правый сектор, без нижней части экрана.
        val defaultLeft = (width * 0.52f).toInt()
        val defaultTop = (height * 0.02f).toInt()
        val defaultRight = (width * 0.99f).toInt()
        val defaultBottom = (height * 0.33f).toInt()

        // Пересечение expected + default сильно снижает ложные срабатывания.
        val ixLeft = max(expectedLeft, defaultLeft).coerceAtLeast(0)
        val ixTop = max(expectedTop, defaultTop).coerceAtLeast(0)
        val ixRight = min(expectedRight, defaultRight).coerceAtMost(width)
        val ixBottom = min(expectedBottom, defaultBottom).coerceAtMost(height)

        val ixWidth = ixRight - ixLeft
        val ixHeight = ixBottom - ixTop
        val minWidth = (width * 0.10f).toInt().coerceAtLeast(80)
        val minHeight = (height * 0.06f).toInt().coerceAtLeast(80)
        return if (ixWidth >= minWidth && ixHeight >= minHeight) {
            SearchRoi(ixLeft, ixTop, ixRight, ixBottom)
        } else {
            SearchRoi(expectedLeft, expectedTop, expectedRight, expectedBottom)
        }
    }

    private fun compareTemplateAtPoint(
        screenshot: android.graphics.Bitmap,
        template: android.graphics.Bitmap,
        centerX: Int,
        centerY: Int,
        targetSize: Int = 24
    ): Float {
        val safeX = centerX.coerceIn(0, screenshot.width - 1)
        val safeY = centerY.coerceIn(0, screenshot.height - 1)
        val patchSize = max(48, min(screenshot.width, screenshot.height) / 12)
        val left = (safeX - patchSize / 2).coerceIn(0, screenshot.width - 1)
        val top = (safeY - patchSize / 2).coerceIn(0, screenshot.height - 1)
        val right = (left + patchSize).coerceIn(left + 1, screenshot.width)
        val bottom = (top + patchSize).coerceIn(top + 1, screenshot.height)

        val patch = android.graphics.Bitmap.createBitmap(screenshot, left, top, right - left, bottom - top)
        val size = targetSize.coerceIn(20, 32)
        val scaledPatch = android.graphics.Bitmap.createScaledBitmap(patch, size, size, true)
        val scaledTemplate = getOrCreateScaledTemplate(template, size)
        patch.recycle()

        var diff = 0f
        val total = size * size
        val patchPixels = IntArray(total)
        val templatePixels = IntArray(total)
        scaledPatch.getPixels(patchPixels, 0, size, 0, 0, size, size)
        scaledTemplate.getPixels(templatePixels, 0, size, 0, 0, size, size)
        for (y in 0 until size) {
            val rowOffset = y * size
            for (x in 0 until size) {
                val index = rowOffset + x
                val p = patchPixels[index]
                val t = templatePixels[index]
                val pr = (p shr 16) and 0xFF
                val pg = (p shr 8) and 0xFF
                val pb = p and 0xFF
                val tr = (t shr 16) and 0xFF
                val tg = (t shr 8) and 0xFF
                val tb = t and 0xFF
                val dr = abs(pr - tr)
                val dg = abs(pg - tg)
                val db = abs(pb - tb)
                diff += (dr + dg + db) / 3f
            }
        }
        scaledPatch.recycle()

        val meanDiff = diff / total
        // 0..255 -> 1..0
        return (1f - (meanDiff / 255f)).coerceIn(0f, 1f)
    }

    private fun getOrCreateScaledTemplate(
        template: android.graphics.Bitmap,
        size: Int
    ): android.graphics.Bitmap {
        val key = System.identityHashCode(template) * 37 + size
        synchronized(scaledTemplateCacheLock) {
            val cached = scaledTemplateCache[key]
            if (cached != null && !cached.isRecycled) return cached

            val scaled = android.graphics.Bitmap.createScaledBitmap(template, size, size, true)
            if (scaledTemplateCache.size >= 8) {
                val iterator = scaledTemplateCache.entries.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            scaledTemplateCache[key] = scaled
            return scaled
        }
    }
    
}
