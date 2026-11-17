# Keep Native JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Adhora native engine and models
-keep class com.adhora.app.engine.** { *; }
-keep class com.adhora.app.engine.ModelsKt { *; }

# Keep Gson annotations and model fields
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-dontwarn coil.**

# Coroutines
-dontwarn kotlinx.coroutines.**
