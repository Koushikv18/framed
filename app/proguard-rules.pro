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

# Framed Data & Recipe Models
-keep class com.aesthetic.framed.data.model.** { *; }
-keep class com.aesthetic.framed.domain.recipe.** { *; }
