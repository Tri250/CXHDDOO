# ============================================================
# PoseAI Android ProGuard Rules
# ============================================================

# ===== 应用自身代码 =====
-keep class com.poseai.app.** { *; }
-keepclassmembers class com.poseai.app.** { *; }

# ===== ML Kit (姿势检测/人脸检测/微笑检测) =====
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-keep class com.google.android.gms.vision.** { *; }
-keep class com.google.android.gms.common.internal.** { *; }
-keep class com.google.android.gms.common.api.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.android.gms.**
-dontwarn com.google.mlkit.**

# ML Kit 模型加载使用反射
-keep class com.google.mlkit.common.sdkinternal.** { *; }
-keep class com.google.mlkit.common.model.** { *; }
-keep class com.google.mlkit.vision.common.** { *; }
-keep class com.google.mlkit.vision.pose.** { *; }
-keep class com.google.mlkit.vision.face.** { *; }
-keep class com.google.mlkit.vision.interfaces.** { *; }
-keep class com.google.android.gms.internal.vision.** { *; }

# ===== TensorFlow Lite =====
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.nnapi.** { *; }
-dontwarn org.tensorflow.lite.**
-dontwarn org.tensorflow.**

# TFLite 原生库加载
-keepclassmembers class org.tensorflow.lite.NativeInterpreterWrapper { *; }
-keepclassmembers class org.tensorflow.lite.TensorImpl { *; }

# ===== CameraX =====
-keep class androidx.camera.** { *; }
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }
-keep class androidx.camera.video.** { *; }
-dontwarn androidx.camera.**

# ===== Room 数据库 =====
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep class com.poseai.app.data.** { *; }
-keepclassmembers class com.poseai.app.data.** { *; }
-dontwarn androidx.room.**

# Room 生成的实现类 (KSP 生成)
-keep class * extends androidx.room.RoomDatabase_Impl { *; }
-keep class * implements androidx.room.Dao_Impl { *; }
-keep class com.poseai.app.data.AppDatabase_Impl { *; }
-keep class com.poseai.app.data.ShootingDao_Impl { *; }

# Room TypeConverter 保留
-keep class * implements androidx.room.TypeConverter { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
-keep @androidx.room.Entity class * { <fields>; }
-keep @androidx.room.Dao class * { <methods>; }

# ===== DataStore =====
-keep class androidx.datastore.** { *; }
-keep class androidx.datastore.preferences.** { *; }
-keep class androidx.datastore.core.** { *; }
-dontwarn androidx.datastore.**

# ===== Gson =====
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-dontwarn com.google.gson.**

# Gson 序列化模型
-keep class com.poseai.app.model.** { *; }
-keepclassmembers class com.poseai.app.model.** { *; }

# Gson @SerializedName 注解保留
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ===== Kotlin Coroutines =====
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.coroutines.** { *; }

# ===== Compose =====
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.** { *; }
-dontwarn androidx.compose.**

# ===== Navigation Compose =====
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# ===== Coil 图片加载 =====
-keep class coil.** { *; }
-keep class coil.size.** { *; }
-keep class coil.key.** { *; }
-keep class coil.decode.** { *; }
-keep class coil.fetch.** { *; }
-keep class coil.request.** { *; }
-keep class coil.memory.** { *; }
-keep class coil.disk.** { *; }
-dontwarn coil.**

# ===== Media3 / ExoPlayer =====
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ===== Billing =====
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# ===== AndroidX 通用 =====
-keep class androidx.core.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.appcompat.** { *; }
-dontwarn androidx.**

# ===== 保留注解和签名 =====
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault

# ===== 保留枚举 =====
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===== 保留 Native 方法 =====
-keepclasseswithmembernames class * {
    native <methods>;
}

# ===== 保留 R 和 BuildConfig =====
-keep class com.poseai.app.R { *; }
-keep class com.poseai.app.R$* { *; }
-keep class com.poseai.app.BuildConfig { *; }

# ===== Compose Metadata 保留（防止 R8 移除 Modifier/Composable 信息）=====
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.runtime.** { *; }
-keep @androidx.compose.runtime.Composable class * { *; }
-keepclasseswithmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ===== Compose Material3 主题/样式 =====
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.icons.** { *; }

# ===== Haptics / Vibrator 反射兼容 =====
-keep class android.os.VibratorManager { *; }
-keep class android.os.VibrationEffect { *; }
-keep class com.poseai.app.util.Haptics* { *; }
-keep class com.poseai.app.util.HapticController { *; }

# ===== TFLite 模型资产（保持不被压缩）=====
-keep class org.tensorflow.lite.Interpreter { *; }
-keepclassmembers class org.tensorflow.lite.NativeInterpreterWrapper {
    native <methods>;
    public <methods>;
}

# ===== FileProvider 兼容性（不同 Android 版本）=====
-keep class androidx.core.content.FileProvider { *; }
-keep class androidx.core.content.FileProvider$* { *; }

# ===== 保留 Parcelable =====
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ===== 保留 Serializable =====
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ===== Gson TypeToken 保留（防止泛型擦除导致反序列化失败）=====
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken {
    <fields>;
}

# ===== CustomPoseStore 内部 DTO（Gson 反射序列化）=====
-keep class com.poseai.app.store.CustomPoseStore$CustomPoseDto { *; }
