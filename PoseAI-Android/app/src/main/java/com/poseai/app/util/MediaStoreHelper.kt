package com.poseai.app.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream

private const val TAG = "MediaStoreHelper"

/**
 * MediaStore 辅助工具：将应用私有的照片/视频文件写入系统 MediaStore，
 * 使其在系统相册（Gallery / Photos）中可见。
 *
 * Android 10+ 使用 MediaStore IS_PENDING 写入模式；
 * Android 9- 使用 MediaScannerConnection 扫描文件。
 */
object MediaStoreHelper {

    /**
     * 将照片文件添加到系统相册
     *
     * @param context 上下文
     * @param file 照片文件（必须已存在）
     * @return MediaStore 中的 content URI，或 null 表示失败
     */
    fun addImageToGallery(context: Context, file: File): Uri? {
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "Image file does not exist or is empty: ${file.absolutePath}")
            return null
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addImageToGalleryQPlus(context, file)
            } else {
                addImageToGalleryLegacy(context, file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add image to gallery", e)
            // 降级：直接扫描文件
            scanFile(context, file)
            null
        }
    }

    /**
     * 将视频文件添加到系统相册
     *
     * @param context 上下文
     * @param file 视频文件（必须已存在）
     * @return MediaStore 中的 content URI，或 null 表示失败
     */
    fun addVideoToGallery(context: Context, file: File): Uri? {
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "Video file does not exist or is empty: ${file.absolutePath}")
            return null
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addVideoToGalleryQPlus(context, file)
            } else {
                addVideoToGalleryLegacy(context, file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add video to gallery", e)
            scanFile(context, file)
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Android 10+ (API 29+)：使用 MediaStore IS_PENDING 模式
    // ═══════════════════════════════════════════════════════════════

    private fun addImageToGalleryQPlus(context: Context, file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, mimeTypeFromExtension(file.name))
            put(MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/PoseAI")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        return try {
            // 将文件内容写入 MediaStore URI
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(file).use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }

            // 清除 IS_PENDING 标志，使文件对其他应用可见
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)

            Log.i(TAG, "Image added to gallery: $uri")
            uri
        } catch (e: Exception) {
            // 写入失败，删除已创建的 MediaStore 条目
            context.contentResolver.delete(uri, null, null)
            Log.e(TAG, "Failed to write image to MediaStore", e)
            null
        }
    }

    private fun addVideoToGalleryQPlus(context: Context, file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_MOVIES + "/PoseAI")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(file).use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)

            Log.i(TAG, "Video added to gallery: $uri")
            uri
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            Log.e(TAG, "Failed to write video to MediaStore", e)
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Android 9- (API 28-)：使用 MediaScannerConnection
    // ═══════════════════════════════════════════════════════════════

    private fun addImageToGalleryLegacy(context: Context, file: File): Uri? {
        // Android 9-：文件需要位于共享存储目录才能被扫描
        // 先将文件复制到 Pictures/PoseAI 目录
        val picturesDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),
            "PoseAI"
        )
        picturesDir.mkdirs()
        val destFile = File(picturesDir, file.name)
        file.copyTo(destFile, overwrite = true)

        // 触发 MediaScanner 扫描
        scanFile(context, destFile)

        // 返回文件 URI（Android 9- 不需要 content URI）
        Log.i(TAG, "Image added to gallery (legacy): ${destFile.absolutePath}")
        return Uri.fromFile(destFile)
    }

    private fun addVideoToGalleryLegacy(context: Context, file: File): Uri? {
        val moviesDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
            "PoseAI"
        )
        moviesDir.mkdirs()
        val destFile = File(moviesDir, file.name)
        file.copyTo(destFile, overwrite = true)

        scanFile(context, destFile)

        Log.i(TAG, "Video added to gallery (legacy): ${destFile.absolutePath}")
        return Uri.fromFile(destFile)
    }

    // ═══════════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 触发 MediaScanner 扫描指定文件，使系统相册更新
     */
    fun scanFile(context: Context, file: File) {
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(mimeTypeFromExtension(file.name)),
                null
            )
        } catch (e: Exception) {
            Log.w(TAG, "MediaScanner scan failed", e)
        }
    }

    private fun mimeTypeFromExtension(filename: String): String {
        return when {
            filename.endsWith(".jpg", ignoreCase = true) -> "image/jpeg"
            filename.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            filename.endsWith(".webp", ignoreCase = true) -> "image/webp"
            filename.endsWith(".png", ignoreCase = true) -> "image/png"
            filename.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            filename.endsWith(".3gp", ignoreCase = true) -> "video/3gp"
            else -> "application/octet-stream"
        }
    }
}
