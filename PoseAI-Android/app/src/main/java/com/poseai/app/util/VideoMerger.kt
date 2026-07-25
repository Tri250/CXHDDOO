package com.poseai.app.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID

object VideoMerger {

    private const val TAG = "VideoMerger"
    private const val SAMPLE_TIME = 1000000L

    suspend fun merge(
        videoFiles: List<File>,
        bgmFile: File? = null,
        outputDir: File
    ): File? = withContext(Dispatchers.IO) {
        if (videoFiles.isEmpty()) return@withContext null

        val outputFile = File(outputDir, "${UUID.randomUUID()}_final.mp4")
        var muxer: MediaMuxer? = null

        try {
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var bgmTrackIndex = -1
            var totalVideoDuration = 0L

            val firstExtractor = MediaExtractor()
            firstExtractor.setDataSource(videoFiles[0].absolutePath)
            for (i in 0 until firstExtractor.trackCount) {
                val format = firstExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoTrackIndex = muxer.addTrack(format)
                } else if (mime.startsWith("audio/")) {
                    audioTrackIndex = muxer.addTrack(format)
                }
            }
            firstExtractor.release()

            if (bgmFile != null && bgmFile.exists()) {
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
            val buffer = ByteBuffer.allocate(256 * 1024)

            for (videoIndex in videoFiles.indices) {
                val videoFile = videoFiles[videoIndex]
                val extractor = MediaExtractor()
                extractor.setDataSource(videoFile.absolutePath)

                for (trackType in 0..1) {
                    val trackMime = if (trackType == 0) "video/" else "audio/"
                    val targetTrack = if (trackType == 0) videoTrackIndex else audioTrackIndex

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
                extractor.release()
            }

            if (bgmFile != null && bgmFile.exists() && bgmTrackIndex >= 0) {
                writeBgmLooped(muxer, bgmTrackIndex, bgmFile, totalVideoDuration, bufferInfo, buffer)
            }

            muxer.stop()
            muxer.release()

            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Merge failed", e)
            try {
                muxer?.stop()
                muxer?.release()
            } catch (_: Exception) {}
            null
        }
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
