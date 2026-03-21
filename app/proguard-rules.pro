# ProGuard rules for Egg Inc Autoclicker

# OpenCV
# Keep only classes reached by public API usage from app code.
-keep class org.opencv.core.** { *; }
-keep class org.opencv.imgproc.** { *; }
-dontwarn org.opencv.**

# Gson
-keep class com.egginc.autoclicker.utils.ClickerConfig { *; }
-keep class com.egginc.autoclicker.utils.Point { *; }
-keep class com.egginc.autoclicker.utils.RectArea { *; }
-keep class com.egginc.autoclicker.utils.RGBColor { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Accessibility Service
-keep class com.egginc.autoclicker.service.ClickerAccessibilityService { *; }
