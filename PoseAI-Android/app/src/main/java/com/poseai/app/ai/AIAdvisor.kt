package com.poseai.app.ai

import android.graphics.Bitmap
import com.poseai.app.model.SceneType
import kotlinx.coroutines.delay

/**
 * AI 穿搭顾问——转换自 iOS AIAdvisor。
 * Step 14 多模态穿搭结合场景建议。Android 端同样提供离线秒降级体验引擎。
 */
object AIAdvisor {

    /** 利用视觉能力解析 OOTD 并结合场景给出情感价值建议（离线降级） */
    suspend fun analyzeOOTD(image: Bitmap?, currentScene: SceneType): String {
        // 预留真实多模态接口（可将 image 压缩后上传）
        delay(1200) // 模拟大尺寸图片上传延迟

        val mockOOTDS = listOf("飘逸长裙", "休闲针织衫", "干练风衣", "日常慵懒风", "时尚休闲套装")
        val currentOOTD = mockOOTDS.random()
        val sceneName = currentScene.displayName

        return when (currentOOTD) {
            "飘逸长裙" ->
                "捕捉到您今天穿着${currentOOTD}，非常绝美！尝试双手微微拎起裙摆，在这个${sceneName}中心旋转一下，我们会抓拍那飞扬的一刻！"
            "干练风衣" ->
                "这套${currentOOTD}太有高级质感了。在这个${sceneName}建议您稍微整理一下衣领然后单手插兜，眼神不用看我，看向远方极其出片！"
            "休闲针织衫", "日常慵懒风", "时尚休闲套装" ->
                "监测到了非常舒服的${currentOOTD}穿搭！在这片${sceneName}不要拘束，像平时伸懒腰一样大幅度伸展双臂，我要抓下这段松弛感。"
            else ->
                "您的这身穿搭与这里的${sceneName}绝配，尝试侧对屏幕，来个自然的回眸一笑吧！"
        }
    }
}