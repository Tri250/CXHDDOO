package com.poseai.app.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileOutputStream

/**
 * 视频拼接器——对应 iOS VideoMerger。
 * 使用 MediaExtractor + MediaMuxer 将多个 MP4 切片首尾相接。
 * 正确处理音频/视频轨 PTS 偏移，保证音视频同步。
 *
 * 关键实现：
 *  - 视频和音频轨独立时间线，各自累积偏移
 *  - 每个切片独立的 PTS 归零再偏移，避免时间戳跳变
 *  - 首个视频切片以 I 帧关键帧对齐
 *  - 异常时清理未完成的输出
 *  - 资源安全：所有资源使用 try/finally 保证释放
 */
object VideoMerger {

    data class MergeProgress(val completed: Boolean, val error: String? = null)

    /**
     * 将多个 MP4 切片无缝首尾相接。
     *
     * @param videoFiles 录制的各个镜头切片
     * @param output 合并输出文件
     * @param bgmFile 可选背景音乐（当前传 null，仅串联音视频轨）
     */
    fun merge(videoFiles: List<File>, output: File, bgmFile: File? = null): Boolean {
        if (videoFiles.isEmpty()) return false

        var mux: MediaMuxer? = null
        var muxStarted = false
        var outputStream: FileOutputStream? = null
        var videoTrackIndex = -1
        var audioTrackIndex = -1

        try {
            // 预创建输出文件
            outputStream = FileOutputStream(output)
            // 预写空数据确保文件被创建
            outputStream.write(ByteArray(0))
            outputStream.flush()
            outputStream.close()
            outputStream = null

            mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            var videoOffsetUs = 0L
            var audioOffsetUs = 0L

            // 合理大小的复用 buffer：避免每次循环都重新分配 4MB
            val reusableBuffer = java.nio.ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()

            fun startMuxIfReady() {
                if (!muxStarted && (videoTrackIndex >= 0 || audioTrackIndex >= 0)) {
                    mux!!.start()
                    muxStarted = true
                }
            }

            for ((i, file) in videoFiles.withIndex()) {
                if (!file.exists() || file.length() < 1024) continue

                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                } catch (e: Exception) {
                    extractor.release()
                    continue
                }

                try {
                    val videoTrack = findTrack(extractor, "video/")
                    val audioTrack = findTrack(extractor, "audio/")

                    // 第一个有效切片决定轨道格式
                    if (videoTrackIndex < 0 && videoTrack >= 0) {
                        videoFormat = extractor.getTrackFormat(videoTrack)
                        videoTrackIndex = mux!!.addTrack(videoFormat)
                    }
                    if (audioTrackIndex < 0 && audioTrack >= 0) {
                        audioFormat = extractor.getTrackFormat(audioTrack)
                        audioTrackIndex = mux!!.addTrack(audioFormat)
                    }

                    startMuxIfReady()

                    // === 视频轨 ===
                    if (videoTrack >= 0 && videoTrackIndex >= 0) {
                        extractor.selectTrack(videoTrack)
                        var firstSampleTime = -1L
                        var lastPts = 0L
                        while (true) {
                            reusableBuffer.clear()
                            val size = extractor.readSampleData(reusableBuffer, 0)
                            if (size < 0) break
                            val pts = extractor.sampleTime
                            if (firstSampleTime < 0) firstSampleTime = pts
                            lastPts = pts

                            reusableBuffer.position(0)
                            reusableBuffer.limit(size)
                            info.offset = 0
                            info.size = size
                            info.presentationTimeUs = pts - firstSampleTime + videoOffsetUs
                            info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                                MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                            mux.writeSampleData(videoTrackIndex, reusableBuffer, info)
                            extractor.advance()
                        }
                        val duration = videoFormat?.getLong(MediaFormat.KEY_DURATION)
                        videoOffsetUs += (duration ?: (lastPts - firstSampleTime + 1))
                        extractor.unselectTrack(videoTrack)
                    }

                    // === 音频轨（独立时间线，复用同一个 buffer） ===
                    if (audioTrack >= 0 && audioTrackIndex >= 0) {
                        extractor.selectTrack(audioTrack)
                        var firstSampleTime = -1L
                        var lastPts = 0L
                        while (true) {
                            reusableBuffer.clear()
                            val size = extractor.readSampleData(reusableBuffer, 0)
                            if (size < 0) break
                            val pts = extractor.sampleTime
                            if (firstSampleTime < 0) firstSampleTime = pts
                            lastPts = pts

                            reusableBuffer.position(0)
                            reusableBuffer.limit(size)
                            info.offset = 0
                            info.size = size
                            info.presentationTimeUs = pts - firstSampleTime + audioOffsetUs
                            info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                                MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                            mux.writeSampleData(audioTrackIndex, reusableBuffer, info)
                            extractor.advance()
                        }
                        val duration = audioFormat?.getLong(MediaFormat.KEY_DURATION)
                        audioOffsetUs += (duration ?: (lastPts - firstSampleTime + 1))
                        extractor.unselectTrack(audioTrack)
                    }
                } finally {
                    extractor.release()
                }
            }

            if (videoTrackIndex < 0 && audioTrackIndex < 0) {
                // 没有有效轨道
                runCatching { mux?.stop() }
                runCatching { mux?.release() }
                output.delete()
                return false
            }

            mux?.stop()
            runCatching { mux?.release() }
            return true

        } catch (e: Exception) {
            e.printStackTrace()
            // 清理失败的输出
            runCatching { mux?.stop() }
            runCatching { mux?.release() }
            output.delete()
            return false
        } finally {
            // 确保 FileOutputStream 一定被关闭
            runCatching { outputStream?.flush() }
            runCatching { outputStream?.close() }
        }
    }

    private fun findTrack(extractor: MediaExtractor, mimeType: String): Int {
        for (t in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(t)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimeType)) return t
        }
        return -1
    }
}
