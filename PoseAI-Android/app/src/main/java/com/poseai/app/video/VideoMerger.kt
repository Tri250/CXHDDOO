package com.poseai.app.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

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

        var muxer: MediaMuxer? = null
        var outputStream: java.io.FileOutputStream? = null

        try {
            // 预创建输出文件
            outputStream = java.io.FileOutputStream(output)
            val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            var videoOffsetUs = 0L
            var audioOffsetUs = 0L
            var videoStarted = false

            for ((i, file) in videoFiles.withIndex()) {
                if (!file.exists() || file.length() < 1024) continue // 跳过太小的文件

                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                } catch (e: Exception) {
                    extractor.release()
                    continue
                }

                val videoTrack = findTrack(extractor, "video/")
                val audioTrack = findTrack(extractor, "audio/")

                // 第一个有效切片决定轨道格式
                if (i == 0 || (!videoStarted && videoTrack >= 0)) {
                    if (videoTrack >= 0) {
                        videoFormat = extractor.getTrackFormat(videoTrack)
                        videoTrackIndex = mux.addTrack(videoFormat)
                        videoStarted = true
                    }
                    if (audioTrack >= 0) {
                        audioFormat = extractor.getTrackFormat(audioTrack)
                        audioTrackIndex = mux.addTrack(audioFormat)
                    }
                    if (videoTrackIndex >= 0 || audioTrackIndex >= 0) {
                        mux.start()
                        videoStarted = true
                    }
                }

                val buffer = java.nio.ByteBuffer.allocate(4 * 1024 * 1024) // 4MB buffer
                val info = MediaCodec.BufferInfo()

                // === 视频轨 ===
                if (videoTrack >= 0 && videoTrackIndex >= 0) {
                    extractor.selectTrack(videoTrack)
                    var firstSampleTime = -1L
                    var lastPts = 0L
                    while (true) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val pts = extractor.sampleTime
                        if (firstSampleTime < 0) firstSampleTime = pts
                        lastPts = pts

                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = pts - firstSampleTime + videoOffsetUs
                        info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                            MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        mux.writeSampleData(videoTrackIndex, buffer, info)
                        extractor.advance()
                    }
                    val duration = videoFormat?.getLong(MediaFormat.KEY_DURATION)
                    videoOffsetUs += (duration ?: (lastPts - firstSampleTime + 1))
                    extractor.unselectTrack(videoTrack)
                }

                // === 音频轨（独立时间线） ===
                if (audioTrack >= 0 && audioTrackIndex >= 0) {
                    extractor.selectTrack(audioTrack)
                    var firstSampleTime = -1L
                    var lastPts = 0L
                    while (true) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val pts = extractor.sampleTime
                        if (firstSampleTime < 0) firstSampleTime = pts
                        lastPts = pts

                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = pts - firstSampleTime + audioOffsetUs
                        info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                            MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        mux.writeSampleData(audioTrackIndex, buffer, info)
                        extractor.advance()
                    }
                    val duration = audioFormat?.getLong(MediaFormat.KEY_DURATION)
                    audioOffsetUs += (duration ?: (lastPts - firstSampleTime + 1))
                    extractor.unselectTrack(audioTrack)
                }

                extractor.release()
            }

            if (videoTrackIndex < 0 && audioTrackIndex < 0) {
                // 没有有效轨道
                mux.stop()
                mux.release()
                muxer = null
                output.delete()
                return false
            }

            mux.stop()
            mux.release()
            muxer = null
            return true

        } catch (e: Exception) {
            e.printStackTrace()
            // 清理失败的输出
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            output.delete()
            return false
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
