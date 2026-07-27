package com.poseai.app

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.room.Room
import com.poseai.app.data.AppDatabase
import com.poseai.app.engine.AIModelManager
import com.poseai.app.store.CustomPoseStore
import com.poseai.app.store.StoreManager

class PoseAIApp : Application() {

    companion object {
        private const val TAG = "PoseAIApp"
        lateinit var instance: PoseAIApp
            private set

        fun get(): PoseAIApp = instance

        fun getDatabase(): AppDatabase = instance.database

        fun getStoreManager(): StoreManager = instance.storeManager

        fun getAIModelManager(): AIModelManager = instance.aiModelManager

        fun getCustomPoseStore(): CustomPoseStore = instance.customPoseStore
    }

    lateinit var database: AppDatabase
        private set

    lateinit var storeManager: StoreManager
        private set

    lateinit var aiModelManager: AIModelManager
        private set

    lateinit var customPoseStore: CustomPoseStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 全局未捕获异常处理：防止相机/ML Kit/TFLite 在某些机型上崩溃导致整个 App 闪退
        // 崩溃时记录日志，便于用户反馈问题时定位
        setupUncaughtExceptionHandler()

        try {
            database = Room.databaseBuilder(
                this,
                AppDatabase::class.java,
                AppDatabase.DATABASE_NAME
            ).fallbackToDestructiveMigration().build()
            Log.i(TAG, "Database initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Database init failed, using in-memory fallback", e)
            database = Room.inMemoryDatabaseBuilder(this, AppDatabase::class.java)
                .allowMainThreadQueries().build()
        }
        try {
            storeManager = StoreManager(this)
            Log.i(TAG, "StoreManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "StoreManager init failed", e)
            throw e // DataStore 是核心组件,无法降级
        }
        try {
            aiModelManager = AIModelManager(this)
            Log.i(TAG, "AIModelManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "AIModelManager init failed, creating empty fallback", e)
            aiModelManager = AIModelManager(this)
        }
        try {
            customPoseStore = CustomPoseStore(this)
            Log.i(TAG, "CustomPoseStore initialized")
        } catch (e: Exception) {
            Log.e(TAG, "CustomPoseStore init failed", e)
        }
    }

    /**
     * 全局未捕获异常处理器
     *
     * 目的：在 TFLite/ML Kit/CameraX 在某些国产 ROM（MIUI/EMUI/ColorOS）上偶发崩溃时，
     * 记录错误日志到 Logcat，避免完全无信息的闪退。
     *
     * 不阻止默认的进程终止行为，仅补充日志。
     */
    private fun setupUncaughtExceptionHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e(TAG, "Uncaught exception on ${thread.name}", throwable)
                Log.e(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT}), App versionCode: ${packageManager.getPackageInfo(packageName, 0).let { it.longVersionCode.toInt() }}")
            } catch (_: Throwable) {
                // 记录日志失败时不再二次抛出
            }
            // 转交默认处理器，让系统正常终止进程
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
