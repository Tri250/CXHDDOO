# ProGuard / R8 规则 - PoseAI Release 构建

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============ ML Kit ============
# ML Kit Pose Detection
-keep class com.google.mlkit.vision.pose.** { *; }
-keep class com.google.mlkit.vision.pose.defaults.** { *; }
# ML Kit Image Labeling
-keep class com.google.mlkit.vision.label.** { *; }
-keep class com.google.mlkit.vision.label.defaults.** { *; }
# ML Kit Common
-keep class com.google.mlkit.common.** { *; }

# ============ CameraX ============
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ============ Compose ============
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.animation.** { *; }
-dontwarn androidx.compose.**

# ============ Room ============
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# Keep Room database entities and DAOs
-keep class com.poseai.app.data.** { *; }
-keep class com.poseai.app.model.** { *; }

# ============ AndroidX ============
-dontwarn androidx.**
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }

# ============ Kotlin ============
-keep class kotlin.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ============ App Models ============
-keep class com.poseai.app.model.** { *; }
-keep class com.poseai.app.ml.** { *; }
-keep class com.poseai.app.ai.** { *; }

# Keep enums with values (for when expressions in serialized data)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep data classes used for serialization
-keepclassmembers class * extends java.lang.Enum {
    <fields>;
}

# Reflection / Serialization
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Allow optimization but keep important methods
-keepclassmembers class * {
    public *;
}
-dontoptimize

# ============ Desugaring ============
-dontwarn java.time.**
-dontwarn java.util.stream.**
-dontwarn java.util.function.**

# ============ Suppress common warnings ============
-dontwarn org.json.**
-dontwarn android.net.**
-dontwarn android.media.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable
-keep class * extends android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}