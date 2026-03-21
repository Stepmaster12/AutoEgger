package com.egginc.autoclicker.utils

/**
 * Единая математика преобразования координат между базовым и текущим экраном.
 */
object ScreenMapper {

    fun baseToScreenX(baseX: Int, baseWidth: Int, screenWidth: Int): Int {
        if (baseWidth <= 0 || screenWidth <= 0) return baseX
        return ((baseX * screenWidth.toFloat()) / baseWidth).toInt().coerceIn(0, screenWidth - 1)
    }

    fun baseToScreenY(baseY: Int, baseHeight: Int, screenHeight: Int): Int {
        if (baseHeight <= 0 || screenHeight <= 0) return baseY
        return ((baseY * screenHeight.toFloat()) / baseHeight).toInt().coerceIn(0, screenHeight - 1)
    }

    fun screenToBaseX(screenX: Int, screenWidth: Int, baseWidth: Int): Int {
        if (screenWidth <= 0 || baseWidth <= 0) return screenX
        return ((screenX * baseWidth.toFloat()) / screenWidth).toInt().coerceIn(0, baseWidth - 1)
    }

    fun screenToBaseY(screenY: Int, screenHeight: Int, baseHeight: Int): Int {
        if (screenHeight <= 0 || baseHeight <= 0) return screenY
        return ((screenY * baseHeight.toFloat()) / screenHeight).toInt().coerceIn(0, baseHeight - 1)
    }

    fun baseToScreen(point: Point, config: ClickerConfig, screenWidth: Int, screenHeight: Int): Point {
        return Point(
            x = baseToScreenX(point.x, config.baseResolutionWidth, screenWidth),
            y = baseToScreenY(point.y, config.baseResolutionHeight, screenHeight)
        )
    }

    fun toNormalized(point: Point, baseWidth: Int, baseHeight: Int): NormalizedPoint {
        val safeBaseW = baseWidth.coerceAtLeast(1)
        val safeBaseH = baseHeight.coerceAtLeast(1)
        val clampedX = point.x.coerceIn(0, safeBaseW - 1)
        val clampedY = point.y.coerceIn(0, safeBaseH - 1)
        return NormalizedPoint(
            x = clampedX.toDouble() / safeBaseW.toDouble(),
            y = clampedY.toDouble() / safeBaseH.toDouble()
        )
    }

    fun normalizedToBase(point: NormalizedPoint, baseWidth: Int, baseHeight: Int): Point {
        val safeBaseW = baseWidth.coerceAtLeast(1)
        val safeBaseH = baseHeight.coerceAtLeast(1)
        val normalizedX = point.x.coerceIn(0.0, 1.0)
        val normalizedY = point.y.coerceIn(0.0, 1.0)
        return Point(
            x = (normalizedX * safeBaseW).toInt().coerceIn(0, safeBaseW - 1),
            y = (normalizedY * safeBaseH).toInt().coerceIn(0, safeBaseH - 1)
        )
    }
}
