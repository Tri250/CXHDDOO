package com.poseai.app

import android.app.Application
import com.poseai.app.filter.PhotoFilterEngine

class PoseAIApplication : Application() {
    val database: com.poseai.app.data.AppDatabase by lazy {
        com.poseai.app.data.AppDatabase.get(this)
    }

    override fun onCreate() {
        super.onCreate()
        com.poseai.app.ai.AIAdvisor.init(this)
    }

    /**
     * 内存紧张时释放非必要缓存。
     * 不关闭 AIAdvisor 的 labeler —— ML Kit 重新初始化成本高，且关闭后后续 OOTD 分析会 IllegalStateException。
     * 仅释放 PhotoFilterEngine 缓存（可即时重建）。
     */
    override fun onLowMemory() {
        super.onLowMemory()
        runCatching { PhotoFilterEngine.clear() }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= TRIM_MEMORY_MODERATE -> runCatching { PhotoFilterEngine.clear() }
            level >= TRIM_MEMORY_BACKGROUND -> runCatching { PhotoFilterEngine.clear() }
        }
    }
}
