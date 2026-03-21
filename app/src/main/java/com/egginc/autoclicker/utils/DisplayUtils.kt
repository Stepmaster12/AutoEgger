package com.egginc.autoclicker.utils

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

object DisplayUtils {
    fun getRealScreenDimensions(context: Context): Pair<Int, Int> {
        return try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowMetrics = windowManager.currentWindowMetrics
                Pair(windowMetrics.bounds.width(), windowMetrics.bounds.height())
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
                Pair(metrics.widthPixels, metrics.heightPixels)
            }
        } catch (_: Exception) {
            Pair(1440, 3120)
        }
    }
}
