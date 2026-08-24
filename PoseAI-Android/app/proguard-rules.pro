# ProGuard / R8 规则 - PoseAI Release 构建
# 经过深度优化的版本：保留必要反射、移除过宽的 keep 规则

# ============ 通用 ============
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ============ ML Kit ============
-keep class com.google.mlkit.vision.pose.** { *; }
-keep class com.google.mlkit.vision.pose.defaults.** { *; }
-keep class com.google.mlkit.vision.label.** { *; }
-keep class com.google.mlkit.vision.label.defaults.** { *; }
-keep class com.google.mlkit.common.** { *; }
-dontwarn com.google.mlkit.**

# ============ CameraX ============
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ============ Compose ============
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ============ Room ============
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keep @androidx.room.Database class *
-dontwarn androidx.room.**

# ============ 应用数据模型 ============
-keep class com.poseai.app.data.** { *; }
-keep class com.poseai.app.model.** { *; }
-keep class com.poseai.app.ml.** { *; }
-keep class com.poseai.app.ai.** { *; }
-keep class com.poseai.app.design.** { *; }
-keep class com.poseai.app.util.** { *; }

# ============ 枚举与数据类 ============
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * extends java.lang.Enum {
    <fields>;
}

# ============ Kotlin 协程 ============
-keep class kotlin.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ============ AndroidX ============
-dontwarn androidx.**
-keep class androidx.lifecycle.** { *; }

# ============ Parcelable / Serializable ============
-keep class * extends android.os.Parcelable {
    public static final ** CREATOR;
}
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

# ============ 反射保留 ============
# 保留 Room DAO 方法（被 Room 编译器生成的代码通过反射调用）
-keepclassmembers interface * {
    @androidx.room.* <methods>;
}
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# 保留 Data Class 的 componentN / copy 方法（可能被反射使用）
-keepclassmembers class kotlin.** {
    ** component*();
    ** copy(...);
}

# ============ 本地方法 ============
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============ 抑制常见告警 ============
-dontwarn org.json.**
-dontwarn android.net.**
-dontwarn android.media.**
-dontwarn java.time.**
-dontwarn java.util.stream.**
-dontwarn java.util.function.**