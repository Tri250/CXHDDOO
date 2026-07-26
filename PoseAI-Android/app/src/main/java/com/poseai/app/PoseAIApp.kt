package com.poseai.app

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.poseai.app.data.AppDatabase
import com.poseai.app.engine.AIModelManager
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
    }

    lateinit var database: AppDatabase
        private set

    lateinit var storeManager: StoreManager
        private set

    lateinit var aiModelManager: AIModelManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
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
    }
}
