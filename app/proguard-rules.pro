# ProGuard rules for Framed Android (Production-Hardening Pass)

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# CameraX & Camera2Interop
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-keepclassmembers class * {
    @androidx.camera.camera2.interop.ExperimentalCamera2Interop <methods>;
}

# ExifInterface
-keep class androidx.exifinterface.media.ExifInterface { *; }
-dontwarn androidx.exifinterface.media.**

# OpenGL ES & EGL Native calls
-keepclasseswithmembers class * {
    native <methods>;
}
-keep class android.opengl.** { *; }
-keep class javax.microedition.khronos.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# MediaPipe Tasks Vision & Core Native Libraries
-keep class com.google.mediapipe.tasks.vision.** { *; }
-keep class com.google.mediapipe.tasks.core.** { *; }
-keep class com.google.mediapipe.framework.** { *; }
-keep class com.google.mediapipe.components.** { *; }
-dontwarn com.google.mediapipe.**
-keepclasseswithmembernames class com.google.mediapipe.** {
    native <methods>;
}

# TensorFlow Lite, GPU Delegate & Support Libraries
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.gpu.**
-keepclasseswithmembernames class org.tensorflow.lite.** {
    native <methods>;
}

# Framed Data & Recipe Models
-keep class com.aesthetic.framed.data.model.** { *; }
-keep class com.aesthetic.framed.domain.recipe.** { *; }
