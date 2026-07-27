package com.poseai.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.view.Surface
import com.poseai.app.engine.FaceLandmarkDetector
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Bitmap ARGB 转 NV21 完整实现
 * NV21 格式：YYYY...VU...（UV交错，V在前）
 */
private fun bitmapToNv21(bitmap: Bitmap): ByteArray {
    val width = bitmap.width
    val height = bitmap.height
    val argb = IntArray(width * height)
    bitmap.getPixels(argb, 0, width, 0, 0, width, height)

    val yuv = ByteArray(width * height * 3 / 2)

    // Y 平面
    for (i in 0 until height) {
        for (j in 0 until width) {
            val pixel = argb[i * width + j]
            val r = (pixel shr 16) and 0xff
            val g = (pixel shr 8) and 0xff
            val b = pixel and 0xff
            val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
            yuv[i * width + j] = y.coerceIn(0, 255).toByte()
        }
    }

    // UV 平面（NV21: VU 交错，每 2x2 采样一次）
    var uvIndex = width * height
    for (i in 0 until height step 2) {
        for (j in 0 until width step 2) {
            val p1 = argb[i * width + j]
            val p2 = if (j + 1 < width) argb[i * width + (j + 1)] else p1
            val p3 = if (i + 1 < height) argb[(i + 1) * width + j] else p1
            val p4 = if (i + 1 < height && j + 1 < width) argb[(i + 1) * width + (j + 1)] else p1

            val r = (((p1 shr 16) and 0xff) + ((p2 shr 16) and 0xff) + ((p3 shr 16) and 0xff) + ((p4 shr 16) and 0xff)) shr 2
            val g = (((p1 shr 8) and 0xff) + ((p2 shr 8) and 0xff) + ((p3 shr 8) and 0xff) + ((p4 shr 8) and 0xff)) shr 2
            val b = ((p1 and 0xff) + (p2 and 0xff) + (p3 and 0xff) + (p4 and 0xff)) shr 2

            val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
            val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

            yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
            yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
        }
    }
    return yuv
}

/**
 * 视频实时美颜/滤镜/变速引擎
 *
 * 功能：
 * - 逐帧应用美颜 + 滤镜 + AR特效
 * - 视频变速（0.5x ~ 2.0x）
 * - 视频裁剪/分割
 * - 逐帧处理 → 重新编码输出
 *
 * 基于 MediaCodec 硬件编解码 + 逐帧 Bitmap 处理。
 */
class VideoBeautyEngine {

    /** 视频处理参数 */
    data class VideoProcessParams(
        val beautyParams: AdvancedBeautyEngine.BeautyParams? = null,
        val makeupParams: MakeupEngine.MakeupParams? = null,
        val skinRepairParams: SkinRepairEngine.SkinRepairParams? = null,
        val filter: PhotoFilterEngine.Filter? = null,
        val filterIntensity: Float = 1f,
        val arEffects: List<ArFaceEffectEngine.ArEffect> = emptyList(),
        val speedMultiplier: Float = 1f,         // 0.5 ~ 2.0
        val trimStartMs: Long = 0,               // 裁剪开始时间
        val trimEndMs: Long = Long.MAX_VALUE     // 裁剪结束时间
    )

    // 引擎实例
    private val faceDetector = FaceLandmarkDetector()
    private val advancedBeauty = AdvancedBeautyEngine()
    private val makeupEngine = MakeupEngine()
    private val skinRepairEngine = SkinRepairEngine()
    private val arEngine = ArFaceEffectEngine()

    /**
     * 处理单帧 Bitmap：应用美颜 + 滤镜 + AR 特效
     *
     * @param frame 输入帧
     * @param params 处理参数
     * @return 处理后的帧
     */
    fun processFrame(frame: Bitmap, params: VideoProcessParams): Bitmap {
        var result = frame.copy(Bitmap.Config.ARGB_8888, true)

        // 1. 人脸检测
        val faceDataList = faceDetector.detect(result)
        val faceData = faceDataList.firstOrNull()

        // 2. 皮肤修复
        params.skinRepairParams?.let { srParams ->
            if (srParams.removeAcne > 0 || srParams.removeSpots > 0 ||
                srParams.removeDarkCircles > 0 || srParams.brightenSkinTone > 0) {
                val temp = skinRepairEngine.applyAll(result, faceData, srParams)
                if (temp !== result) {
                    result.recycle()
                    result = temp
                }
            }
        }

        // 3. 高级美颜
        params.beautyParams?.let { beautyParams ->
            val temp = advancedBeauty.applyAll(result, faceData, beautyParams)
            if (temp !== result) {
                result.recycle()
                result = temp
            }
        }

        // 4. 美妆
        params.makeupParams?.let { makeup ->
            if (makeup.lipstickIntensity > 0 || makeup.blushIntensity > 0 ||
                makeup.eyebrowIntensity > 0 || makeup.eyeshadowIntensity > 0 ||
                makeup.eyelinerIntensity > 0 || makeup.eyelashIntensity > 0) {
                val temp = makeupEngine.applyAll(result, faceData, makeup)
                if (temp !== result) {
                    result.recycle()
                    result = temp
                }
            }
        }

        // 5. 滤镜
        params.filter?.let { filter ->
            if (filter != PhotoFilterEngine.Filter.ORIGINAL) {
                val temp = PhotoFilterEngine.applyFilter(result, filter)
                if (temp !== result) {
                    result.recycle()
                    result = temp
                }
            }
        }

        // 6. AR 特效
        if (params.arEffects.isNotEmpty()) {
            for (effect in params.arEffects) {
                val temp = arEngine.applyEffect(result, effect, faceData?.let {
                    ArFaceEffectEngine.FaceLandmarkDetector.FaceData(
                        leftEye = it.leftEyeCenter,
                        rightEye = it.rightEyeCenter,
                        noseBase = it.noseBase,
                        mouthLeft = it.mouthLeft,
                        mouthRight = it.mouthRight,
                        mouthBottom = it.mouthBottom,
                        faceCenter = it.faceCenter,
                        faceWidth = it.faceWidth,
                        faceHeight = it.faceHeight,
                        rollAngle = it.rollAngle
                    )
                })
                if (temp !== result) {
                    result.recycle()
                    result = temp
                }
            }
        }

        return result
    }

    /**
     * 变速处理：修改帧时间戳
     *
     * @param inputPath 输入视频路径
     * @param outputPath 输出视频路径
     * @param speedMultiplier 变速倍率（0.5 = 慢速，2.0 = 快速）
     */
    fun changeSpeed(inputPath: String, outputPath: String, speedMultiplier: Float): Boolean {
        return try {
            changeSpeedInternal(inputPath, outputPath, speedMultiplier)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 视频裁剪
     */
    fun trimVideo(inputPath: String, outputPath: String, startMs: Long, endMs: Long): Boolean {
        return try {
            trimVideoInternal(inputPath, outputPath, startMs, endMs)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 完整视频处理：美颜 + 滤镜 + 变速 + 裁剪
     *
     * 注意：此方法需要较长时间处理。应在后台线程调用。
     */
    fun processVideo(
        inputPath: String,
        outputPath: String,
        params: VideoProcessParams
    ): Boolean {
        return try {
            processVideoInternal(inputPath, outputPath, params)
        } catch (e: Exception) {
            false
        }
    }

    /** 释放资源 */
    fun release() {
        faceDetector.close()
    }

    // ═══════════════════════════════════════════════════════════════
    // 内部实现
    // ═══════════════════════════════════════════════════════════════

    private fun changeSpeedInternal(inputPath: String, outputPath: String, speedMultiplier: Float): Boolean {
        val extractor = android.media.MediaExtractor()
        extractor.setDataSource(inputPath)

        // 找到视频轨道
        var videoTrackIndex = -1
        var videoFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) {
                videoTrackIndex = i
                videoFormat = format
                break
            }
        }

        if (videoTrackIndex < 0 || videoFormat == null) {
            extractor.release()
            return false
        }

        extractor.selectTrack(videoTrackIndex)

        val mime = videoFormat.getString(MediaFormat.KEY_MIME)!!
        val duration = videoFormat.getLong(MediaFormat.KEY_DURATION)
        val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val frameRate = if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
        } else { 30 }

        // 输出格式
        val outputFormat = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, (frameRate * speedMultiplier).toInt().coerceIn(1, 60))
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrackIndex = -1
        var muxerStarted = false

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(videoFormat, inputSurface, null, 0)
        decoder.start()

        val info = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L
        var processedFrames = 0

        // 解码 → 编码循环
        while (true) {
            // 输入：从 extractor 读取数据到 decoder
            val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
            if (inputBufferIndex >= 0) {
                val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                if (sampleSize > 0) {
                    // 修改时间戳实现变速
                    val presentationTime = extractor.sampleTime
                    val newPresentationTime = (presentationTime / speedMultiplier).toLong()
                    decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, newPresentationTime, extractor.sampleFlags)
                    processedFrames++
                } else {
                    decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                extractor.advance()
            }

            // 输出：decoder → encoder
            val decoderStatus = decoder.dequeueOutputBuffer(info, timeoutUs)
            if (decoderStatus >= 0) {
                val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                decoder.releaseOutputBuffer(decoderStatus, !eos)
                if (eos) {
                    encoder.signalEndOfInputStream()
                    break
                }
            }
        }

        // 从 encoder 读取编码后的数据
        while (true) {
            val encoderStatus = encoder.dequeueOutputBuffer(info, timeoutUs)
            when {
                encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = encoder.outputFormat
                    muxerTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                }
                encoderStatus >= 0 -> {
                    val encodedBuffer = encoder.getOutputBuffer(encoderStatus)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        info.size = 0
                    }
                    if (info.size > 0 && muxerStarted) {
                        encodedBuffer?.position(info.offset)
                        encodedBuffer?.limit(info.offset + info.size)
                        muxer.writeSampleData(muxerTrackIndex, encodedBuffer!!, info)
                    }
                    encoder.releaseOutputBuffer(encoderStatus, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }

        decoder.stop()
        decoder.release()
        encoder.stop()
        encoder.release()
        inputSurface.release()
        extractor.release()
        if (muxerStarted) {
            muxer.stop()
        }
        muxer.release()

        return true
    }

    private fun trimVideoInternal(inputPath: String, outputPath: String, startMs: Long, endMs: Long): Boolean {
        val extractor = android.media.MediaExtractor()
        extractor.setDataSource(inputPath)

        var videoTrackIndex = -1
        var videoFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) {
                videoTrackIndex = i
                videoFormat = format
                break
            }
        }

        if (videoTrackIndex < 0 || videoFormat == null) {
            extractor.release()
            return false
        }

        // 定位到裁剪起点
        extractor.selectTrack(videoTrackIndex)
        extractor.seekTo(startMs * 1000, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val mime = videoFormat.getString(MediaFormat.KEY_MIME)!!
        val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val frameRate = if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
        } else { 30 }

        val outputFormat = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrackIndex = -1
        var muxerStarted = false

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(videoFormat, inputSurface, null, 0)
        decoder.start()

        val info = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L
        val trimDurationUs = (endMs - startMs) * 1000

        while (true) {
            val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
            if (inputBufferIndex >= 0) {
                val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0 || sampleTime > endMs * 1000) {
                    decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                } else {
                    val sampleSize = extractor.readSampleData(inputBuffer!!, 0)
                    if (sampleSize > 0) {
                        val adjustedTime = sampleTime - startMs * 1000
                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, adjustedTime, extractor.sampleFlags)
                    } else {
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                    extractor.advance()
                }
            }

            val decoderStatus = decoder.dequeueOutputBuffer(info, timeoutUs)
            if (decoderStatus >= 0) {
                val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                decoder.releaseOutputBuffer(decoderStatus, !eos)
                if (eos) {
                    encoder.signalEndOfInputStream()
                    break
                }
            }
        }

        while (true) {
            val encoderStatus = encoder.dequeueOutputBuffer(info, timeoutUs)
            when {
                encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                encoderStatus >= 0 -> {
                    val encodedBuffer = encoder.getOutputBuffer(encoderStatus)
                    if (info.size > 0 && muxerStarted && encodedBuffer != null) {
                        encodedBuffer.position(info.offset)
                        encodedBuffer.limit(info.offset + info.size)
                        muxer.writeSampleData(muxerTrackIndex, encodedBuffer, info)
                    }
                    encoder.releaseOutputBuffer(encoderStatus, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }

        decoder.stop()
        decoder.release()
        encoder.stop()
        encoder.release()
        inputSurface.release()
        extractor.release()
        if (muxerStarted) muxer.stop()
        muxer.release()

        return true
    }

    /**
     * 完整视频逐帧处理
     *
     * 1. 使用 MediaExtractor 分析视频参数（帧率、时长、分辨率、音频轨道）
     * 2. 使用 MediaMetadataRetriever 提取每一帧 → 逐帧处理 → 重新编码
     * 3. 复制原始音频轨道到输出（保持音视频同步）
     * 注意：此方法处理时间较长，适合短视频（<30秒）
     */
    private fun processVideoInternal(inputPath: String, outputPath: String, params: VideoProcessParams): Boolean {
        // ═══════════════════════════════════════════════════════════════
        // 阶段 1：分析输入文件
        // ═══════════════════════════════════════════════════════════════
        val extractor = android.media.MediaExtractor()
        extractor.setDataSource(inputPath)

        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") && videoTrackIndex < 0) {
                videoTrackIndex = i
                videoFormat = format
            } else if (mime.startsWith("audio/") && audioTrackIndex < 0) {
                audioTrackIndex = i
                audioFormat = format
            }
        }

        if (videoTrackIndex < 0 || videoFormat == null) {
            extractor.release()
            return false
        }

        val duration = videoFormat.getLong(MediaFormat.KEY_DURATION)
        val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
        val frameRate = if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
        } else {
            30
        }

        // 使用 retriever 获取更精确的帧提取
        val retriever = android.media.MediaMetadataRetriever()
        retriever.setDataSource(inputPath)

        // ═══════════════════════════════════════════════════════════════
        // 阶段 2：创建编码器和 Muxer
        // ═══════════════════════════════════════════════════════════════
        val mime = "video/avc"
        val colorFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        } else {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        }
        val outputFormat = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, (frameRate * params.speedMultiplier).toInt().coerceIn(1, 60))
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoMuxerTrack = -1
        var audioMuxerTrack = -1
        var muxerStarted = false

        // 如果有音频轨道，预先添加到 muxer
        if (audioFormat != null) {
            audioMuxerTrack = muxer.addTrack(audioFormat)
        }

        val info = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L
        val frameIntervalUs = (1_000_000L / frameRate)

        val trimStart = params.trimStartMs * 1000
        val trimEnd = minOf(params.trimEndMs * 1000, duration)

        // ═══════════════════════════════════════════════════════════════
        // 阶段 3：逐帧提取、处理、编码
        // ═══════════════════════════════════════════════════════════════
        var timeUs = trimStart
        while (timeUs <= trimEnd) {
            val frameBitmap = retriever.getFrameAtTime(
                timeUs,
                android.media.MediaMetadataRetriever.OPTION_CLOSEST
            )

            if (frameBitmap != null) {
                val processedFrame = processFrame(frameBitmap, params)
                frameBitmap.recycle()

                val inputBufferIndex = encoder.dequeueInputBuffer(timeoutUs)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                    val adjustedTime = ((timeUs - trimStart) / params.speedMultiplier).toLong()
                    if (inputBuffer != null) {
                        val nv21Data = bitmapToNv21(processedFrame)
                        if (inputBuffer.capacity() >= nv21Data.size) {
                            inputBuffer.clear()
                            inputBuffer.put(nv21Data)
                            encoder.queueInputBuffer(inputBufferIndex, 0, nv21Data.size, adjustedTime, 0)
                        } else {
                            encoder.queueInputBuffer(inputBufferIndex, 0, 0, adjustedTime, 0)
                        }
                    } else {
                        encoder.queueInputBuffer(inputBufferIndex, 0, 0, adjustedTime, 0)
                    }
                    processedFrame.recycle()
                }

                drainEncoder(encoder, muxer, info, timeoutUs, false) { trackIndex, started ->
                    if (!started && trackIndex >= 0) {
                        videoMuxerTrack = trackIndex
                        if (!muxerStarted) {
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                }
            }

            timeUs += frameIntervalUs
        }

        drainEncoder(encoder, muxer, info, timeoutUs, true) { trackIndex, started ->
            if (!started && trackIndex >= 0) {
                videoMuxerTrack = trackIndex
                if (!muxerStarted) {
                    muxer.start()
                    muxerStarted = true
                }
            }
        }

        encoder.stop()
        encoder.release()
        retriever.release()

        // ═══════════════════════════════════════════════════════════════
        // 阶段 4：复制音频轨道（保持音视频同步）
        // ═══════════════════════════════════════════════════════════════
        if (audioTrackIndex >= 0 && audioMuxerTrack >= 0 && muxerStarted) {
            extractor.selectTrack(audioTrackIndex)
            val audioInfo = MediaCodec.BufferInfo()
            val buffer = ByteBuffer.allocate(1024 * 1024)
            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTime = extractor.sampleTime
                // 只复制裁剪范围内的音频
                if (sampleTime in trimStart..trimEnd) {
                    val adjustedTime = ((sampleTime - trimStart) / params.speedMultiplier).toLong()
                    audioInfo.set(0, sampleSize, adjustedTime, extractor.sampleFlags)
                    muxer.writeSampleData(audioMuxerTrack, buffer, audioInfo)
                }
                extractor.advance()
            }
        }

        extractor.release()
        if (muxerStarted) muxer.stop()
        muxer.release()

        return true
    }

    /** 排空编码器输出 */
    private inline fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        info: MediaCodec.BufferInfo,
        timeoutUs: Long,
        endOfStream: Boolean,
        onFormatChange: (trackIndex: Int, started: Boolean) -> Unit
    ) {
        if (endOfStream) {
            encoder.signalEndOfInputStream()
        }

        var muxerTrackIndex = -1
        var muxerStarted = false

        while (true) {
            val encoderStatus = encoder.dequeueOutputBuffer(info, timeoutUs)
            when {
                encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = encoder.outputFormat
                    muxerTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                    onFormatChange(muxerTrackIndex, muxerStarted)
                }
                encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // 编码器暂无输出：非 EOS 模式下退出避免无限循环；EOS 模式下继续等待
                    if (!endOfStream) break
                }
                encoderStatus >= 0 -> {
                    val encodedBuffer = encoder.getOutputBuffer(encoderStatus)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        info.size = 0
                    }
                    if (info.size > 0 && muxerStarted && encodedBuffer != null) {
                        encodedBuffer.position(info.offset)
                        encodedBuffer.limit(info.offset + info.size)
                        muxer.writeSampleData(muxerTrackIndex, encodedBuffer, info)
                    }
                    encoder.releaseOutputBuffer(encoderStatus, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
    }
}
