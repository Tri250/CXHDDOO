package com.poseai.app

import android.app.Application
import com.poseai.app.data.AppDatabase

class PoseAIApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }
}