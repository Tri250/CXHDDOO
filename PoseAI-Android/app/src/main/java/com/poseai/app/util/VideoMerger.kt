package com.poseai.app.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.nio.ByteBuffer
import java.util.UUID

object VideoMerger {

    private const val TAG = "VideoMerger"
    private const val SAMPLE_TIME = 1000000L
    // 段间转场间隙（微秒）：200ms 黑屏过渡
    private const val TRANSITION_GAP_US = 200_000L

    /**
     * 字幕条目：开始时间、结束时间（微秒）、文本内容
     */
    data class SubtitleEntry(
        val startTimeUs: Long,
        val endTimeUs: Long,
        val text: String
    )

    /**
     * Vlog 合并结果：包含视频文件和可选的字幕文件路径
     */
    data class MergeResult(
        val videoFile: File,
        val subtitleFile: File?
    )

    suspend fun merge(
        videoFiles: List<File>,
        bgmFile: File? = null,
        outputDir: File,
        subtitles: List<SubtitleEntry> = emptyList()
    ): File? = withContext(Dispatchers.IO) {
        val result = mergeWithSubtitles(videoFiles, bgmFile, outputDir, subtitles)
        result?.videoFile
    }

    /**
     * 合并视频并生成字幕文件
     * - 视频段间插入 200ms 黑屏过渡（通过时间戳间隙实现）
     * - 生成 SRT 字幕文件，与视频同名
     */
    suspend fun mergeWithSubtitles(
        videoFiles: List<File>,
        bgmFile: File? = null,
        outputDir: File,
        subtitles: List<SubtitleEntry> = emptyList()
    ): MergeResult? = withContext(Dispatchers.IO) {
        if (videoFiles.isEmpty()) return@withContext null

        val validVideoFiles = videoFiles.filter { it.exists() && it.length() > 0 }
        if (validVideoFiles.isEmpty()) {
            Log.e(TAG, "No valid video files to merge")
            return@withContext null
        }

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            Log.e(TAG, "Failed to create output directory")
            return@withContext null
        }

        val outputFile = File(outputDir, "${UUID.randomUUID()}_final.mp4")
        var muxer: MediaMuxer? = null
        var muxerReleased = false

        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var bgmTrackIndex = -1
            var totalVideoDuration = 0L
            // 记录每段视频的开始时间，用于字幕时间戳对齐
            val segmentStartTimes = mutableListOf<Long>()

            val firstExtractor = MediaExtractor()
            try {
                firstExtractor.setDataSource(validVideoFiles[0].absolutePath)
                for (i in 0 until firstExtractor.trackCount) {
                    val format = firstExtractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/")) {
                        videoTrackIndex = muxer.addTrack(format)
                    } else if (mime.startsWith("audio/")) {
                        audioTrackIndex = muxer.addTrack(format)
                    }
                }
            } finally {
                firstExtractor.release()
            }

            if (bgmFile != null && bgmFile.exists() && bgmFile.length() > 0) {
                val bgmExtractor = MediaExtractor()
                try {
                    bgmExtractor.setDataSource(bgmFile.absolutePath)
                    for (i in 0 until bgmExtractor.trackCount) {
                        val format = bgmExtractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                        if (mime.startsWith("audio/")) {
                            bgmTrackIndex = muxer.addTrack(format)
                            break
                        }
                    }
                } finally {
                    bgmExtractor.release()
                }
            }

            muxer.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val buffer = ByteBuffer.allocate(1024 * 1024)

            for (videoIndex in validVideoFiles.indices) {
                val videoFile = validVideoFiles[videoIndex]
                // 记录当前段开始时间
                segmentStartTimes.add(totalVideoDuration)

                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(videoFile.absolutePath)

                    for (trackType in 0..1) {
                        val trackMime = if (trackType == 0) "video/" else "audio/"
                        val targetTrack = if (trackType == 0) videoTrackIndex else audioTrackIndex
                        if (targetTrack < 0) continue

                        for (i in 0 until extractor.trackCount) {
                            val format = extractor.getTrackFormat(i)
                            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                            if (mime.startsWith(trackMime)) {
                                extractor.selectTrack(i)
                                val startTime = totalVideoDuration

                                while (true) {
                                    buffer.clear()
                                    val sampleSize = extractor.readSampleData(buffer, 0)
                                    if (sampleSize < 0) break
                                    if (sampleSize > buffer.capacity()) {
                                        Log.w(TAG, "Sample size $sampleSize exceeds buffer capacity, skipping frame")
                                        extractor.advance()
                                        continue
                                    }

                                    bufferInfo.set(
                                        sampleSize,
                                        0,
                                        startTime + extractor.sampleTime,
                                        extractor.sampleFlags
                                    )

                                    muxer.writeSampleData(targetTrack, buffer, bufferInfo)
                                    extractor.advance()
                                }

                                extractor.unselectTrack(i)
                                break
                            }
                        }
                    }

                    val duration = getVideoDuration(videoFile)
                    totalVideoDuration += duration
                    // 段间转场间隙：在非最后一段后添加 200ms 时间偏移（黑屏过渡）
                    if (videoIndex < validVideoFiles.size - 1) {
                        totalVideoDuration += TRANSITION_GAP_US
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing video file ${videoFile.name}", e)
                } finally {
                    extractor.release()
                }
            }

            if (bgmFile != null && bgmFile.exists() && bgmFile.length() > 0 && bgmTrackIndex >= 0) {
                writeBgmLooped(muxer, bgmTrackIndex, bgmFile, totalVideoDuration, bufferInfo, buffer)
            }

            if (!muxerReleased) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Muxer stop failed", e)
                }
                try {
                    muxer.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Muxer release failed", e)
                }
                muxerReleased = true
            }

            if (outputFile.exists() && outputFile.length() > 0) {
                // 生成 SRT 字幕文件
                var subtitleFile: File? = null
                if (subtitles.isNotEmpty()) {
                    subtitleFile = File(outputFile.parentFile, outputFile.nameWithoutExtension + ".srt")
                    writeSrtFile(subtitleFile, subtitles)
                }
                MergeResult(outputFile, subtitleFile)
            } else {
                Log.e(TAG, "Output file is empty or does not exist")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Merge failed", e)
            null
        } finally {
            if (!muxerReleased && muxer != null) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "muxer stop error", e)
                }
                try {
                    muxer.release()
                } catch (e: Exception) {
                    Log.w(TAG, "muxer release error", e)
                }
                muxerReleased = true
            }
            if (outputFile.exists() && outputFile.length() == 0L) {
                outputFile.delete()
            }
        }
    }

    /**
     * 生成 SRT 格式字幕文件
     * SRT 是最通用的字幕格式，支持所有主流播放器
     */
    private fun writeSrtFile(file: File, subtitles: List<SubtitleEntry>) {
        try {
            FileWriter(file).use { writer ->
                subtitles.forEachIndexed { index, sub ->
                    writer.write("${index + 1}\n")
                    writer.write("${formatSrtTime(sub.startTimeUs)} --> ${formatSrtTime(sub.endTimeUs)}\n")
                    writer.write("${sub.text}\n\n")
                }
            }
            Log.i(TAG, "SRT subtitle file generated: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write SRT file", e)
        }
    }

    /**
     * 将微秒时间戳格式化为 SRT 时间格式：HH:MM:SS,mmm
     */
    private fun formatSrtTime(timeUs: Long): String {
        val totalMs = timeUs / 1000
        val hours = totalMs / 3_600_000
        val minutes = (totalMs % 3_600_000) / 60_000
        val seconds = (totalMs % 60_000) / 1000
        val millis = totalMs % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun getVideoDuration(file: File): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var duration = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    duration = maxOf(duration, format.getLong(MediaFormat.KEY_DURATION))
                }
            }
            duration
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video duration", e)
            0L
        } finally {
            extractor.release()
        }
    }

    private fun writeBgmLooped(
        muxer: MediaMuxer,
        trackIndex: Int,
        bgmFile: File,
        totalDuration: Long,
        bufferInfo: MediaCodec.BufferInfo,
        buffer: ByteBuffer
    ) {
        var loopStartTime = 0L
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(bgmFile.absolutePath)
            var bgmTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    bgmTrackIndex = i
                    extractor.selectTrack(i)
                    break
                }
            }

            if (bgmTrackIndex < 0) return

            while (loopStartTime < totalDuration) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) {
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    val bgmDuration = getVideoDuration(bgmFile)
                    if (bgmDuration <= 0) break
                    loopStartTime += bgmDuration
                    continue
                }

                val presentationTime = loopStartTime + extractor.sampleTime
                if (presentationTime >= totalDuration) break

                bufferInfo.set(
                    sampleSize,
                    0,
                    presentationTime,
                    extractor.sampleFlags
                )

                muxer.writeSampleData(trackIndex, buffer, bufferInfo)
                extractor.advance()
            }
        } finally {
            extractor.release()
        }
    }
}
