package com.poseai.app

import android.app.Application
import com.poseai.app.ai.AIAdvisor
import com.poseai.app.data.AppDatabase

class PoseAIApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        // 初始化 AI 模块（OOTD 分析器、场景分类器等）
        AIAdvisor.init(this)
    }
}