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

# MediaPipe Tasks Vision
-keep class com.google.mediapipe.tasks.vision.** { *; }
-keep class com.google.mediapipe.tasks.core.** { *; }
-dontwarn com.google.mediapipe.**

# TensorFlow Lite & GPU Delegate
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Framed Data & Recipe Models
-keep class com.aesthetic.framed.data.model.** { *; }
-keep class com.aesthetic.framed.domain.recipe.** { *; }
-keep class com.aesthetic.framed.data.model.** { *; }
-keep class com.aesthetic.framed.domain.recipe.** { *; }
