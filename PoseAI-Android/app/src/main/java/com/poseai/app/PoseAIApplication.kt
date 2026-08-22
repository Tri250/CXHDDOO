package com.poseai.app

import android.app.Application
import com.poseai.app.ai.AIAdvisor
import com.poseai.app.data.AppDatabase
import com.poseai.app.filter.PhotoFilterEngine

class PoseAIApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        // 初始化 AI 模块（OOTD 分析器、场景分类器等）
        AIAdvisor.init(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        // 释放 AI 资源
        runCatching { AIAdvisor.close() }
        runCatching { PhotoFilterEngine.clear() }
    }

    /** 内存紧张时主动释放非必要资源，避免 OOM */
    override fun onLowMemory() {
        super.onLowMemory()
        runCatching { PhotoFilterEngine.clear() }
        runCatching { AIAdvisor.close() }
    }
}