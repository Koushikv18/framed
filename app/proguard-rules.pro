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

# Google ML Kit Object Detection & Tracking (Bundled On-Device Model)
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_object_detection.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

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

# Google Generative AI (Gemini SDK) & OkHttp & Serialization
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**
-keepattributes *Annotation*,Signature
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class kotlinx.serialization.** { *; }
-dontwarn okhttp3.**

# WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Framed Data & Recipe Models
-keep class com.aesthetic.framed.data.model.** { *; }
-keep class com.aesthetic.framed.domain.recipe.** { *; }
