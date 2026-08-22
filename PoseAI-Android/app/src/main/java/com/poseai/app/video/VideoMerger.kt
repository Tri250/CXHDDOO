package com.poseai.app.video

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * 视频拼接器——对应 iOS VideoMerger。
 * 使用 MediaExtractor + MediaMuxer 将多个 MP4 切片首尾相接（可选叠加 BGM 音轨）。
 */
object VideoMerger {

    /**
     * 将多个 MP4 切片无缝首尾相接，可选叠加一条 BGM 音轨。
     * @param videoFiles 录制的各个镜头切片
     * @param output 合并输出文件
     * @param bgmFile 可选背景音乐（当前传 null，仅串联音视频轨）
     */
    fun merge(videoFiles: List<File>, output: File, bgmFile: File?): Boolean {
        if (videoFiles.isEmpty()) return false
        try {
            val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null
            var totalOffsetUs = 0L

            for ((i, file) in videoFiles.withIndex()) {
                val extractor = MediaExtractor()
                extractor.setDataSource(file.absolutePath)
                var videoTrack = -1
                var audioTrack = -1
                for (t in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(t)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") && videoTrack < 0) videoTrack = t
                    else if (mime.startsWith("audio/") && audioTrack < 0) audioTrack = t
                }

                // 第一个切片决定轨道配置
                if (i == 0) {
                    if (videoTrack >= 0) {
                        videoFormat = extractor.getTrackFormat(videoTrack)
                        videoTrackIndex = muxer.addTrack(videoFormat)
                    }
                    if (audioTrack >= 0) {
                        audioFormat = extractor.getTrackFormat(audioTrack)
                        audioTrackIndex = muxer.addTrack(audioFormat)
                    }
                    muxer.start()
                }

                val buffer = java.nio.ByteBuffer.allocate(2 * 1024 * 1024)
                val info = android.media.MediaCodec.BufferInfo()

                // 写视频轨
                if (videoTrack >= 0) {
                    extractor.selectTrack(videoTrack)
                    var firstSampleTime = -1L
                    while (true) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val pts = extractor.sampleTime
                        if (firstSampleTime < 0) firstSampleTime = pts
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = pts - firstSampleTime + totalOffsetUs
                        info.flags = if (extractor.sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                            android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        muxer.writeSampleData(videoTrackIndex, buffer, info)
                        extractor.advance()
                    }
                    if (videoFormat != null) {
                        val durationUs = (videoFormat.getLong(MediaFormat.KEY_DURATION))
                        totalOffsetUs += durationUs
                    }
                    extractor.unselectTrack(videoTrack)
                }

                // 写音频轨
                if (audioTrack >= 0 && audioTrackIndex >= 0) {
                    extractor.selectTrack(audioTrack)
                    var firstSampleTime = -1L
                    while (true) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val pts = extractor.sampleTime
                        if (firstSampleTime < 0) firstSampleTime = pts
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = pts - firstSampleTime
                        info.flags = if (extractor.sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                            android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        muxer.writeSampleData(audioTrackIndex, buffer, info)
                        extractor.advance()
                    }
                    extractor.unselectTrack(audioTrack)
                }

                extractor.release()
            }
            muxer.stop()
            muxer.release()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}