package com.poseai.app

import android.app.Application
import androidx.room.Room
import com.poseai.app.data.AppDatabase
import com.poseai.app.engine.AIModelManager
import com.poseai.app.store.StoreManager

class PoseAIApp : Application() {
    lateinit var database: AppDatabase
        private set

    lateinit var storeManager: StoreManager
        private set

    lateinit var aiModelManager: AIModelManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
        storeManager = StoreManager(this)
        aiModelManager = AIModelManager(this)
    }

    companion object {
        lateinit var instance: PoseAIApp
            private set

        fun get(): PoseAIApp = instance

        fun getDatabase(): AppDatabase = instance.database

        fun getStoreManager(): StoreManager = instance.storeManager

        fun getAIModelManager(): AIModelManager = instance.aiModelManager
    }
}
