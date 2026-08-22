package com.poseai.app.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.poseai.app.model.CropRatio

/** 画幅裁切：居中裁切，复刻 iOS CropRatio.apply */
fun Bitmap.applyCrop(ratio: CropRatio): Bitmap {
    if (ratio == CropRatio.ORIGINAL) return this
    val w = width.toFloat()
    val h = height.toFloat()
    val target = ratio.targetRatio
    val current = w / h

    var cropW = w
    var cropH = h
    when {
        current > target -> cropW = h * target
        current < target -> cropH = w / target
        else -> return this
    }

    val x = ((w - cropW) / 2f).toInt().coerceIn(0, width)
    val y = ((h - cropH) / 2f).toInt().coerceIn(0, height)
    val cw = cropW.toInt().coerceIn(1, width - x)
    val ch = cropH.toInt().coerceIn(1, height - y)
    return Bitmap.createBitmap(this, x, y, cw, ch)
}

/** 添加 PoseAI 水印（对应 iOS withPoseAIWatermark） */
fun Bitmap.withPoseAIWatermark(): Bitmap {
    val out = Bitmap.createBitmap(width, height, config ?: Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    canvas.drawBitmap(this, 0f, 0f, null)

    val text = " 📸 Shot on PoseAI "
    val fontSize = maxOf(width, height) * 0.015f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = fontSize
        isFakeBoldText = true
    }
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
    }
    val textWidth = paint.measureText(text)
    val padding = width * 0.02f
    val rect = RectF(
        width - textWidth - padding,
        height - fontSize - padding,
        width - padding,
        height - padding
    )
    val bg = RectF(rect.left - 8, rect.top - 4, rect.right + 8, rect.bottom + 4)
    canvas.drawRoundRect(bg, 8f, 8f, bgPaint)
    canvas.drawText(text, rect.left, rect.bottom, paint)
    return out
}

/** 保存到系统相册（Q+ 用 MediaStore，旧版本写公共目录） */
fun saveToGallery(context: Context, bitmap: Bitmap): Uri? {
    val name = "PoseAI_${System.currentTimeMillis()}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    return try {
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        uri
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/** 分享图片（对应 iOS UIActivityViewController） */
fun shareBitmap(context: Context, bitmap: Bitmap, chooserTitle: String = "分享到") {
    val cacheFile = java.io.File(context.cacheDir, "share_${System.currentTimeMillis()}.jpg")
    java.io.FileOutputStream(cacheFile).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out) }
    val contentUri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        cacheFile
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

/** 从相册 Uri 加载图片（历史图库缩略图/大图用） */
fun loadBitmapFromUri(context: Context, uriString: String): Bitmap? {
    return try {
        val resolver = context.contentResolver
        resolver.openInputStream(Uri.parse(uriString))?.use { input ->
            BitmapFactory.decodeStream(input, null, null)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/** 从相册 Uri 加载压缩缩略图（历史图库网格用，降低内存） */
fun loadThumbnailFromUri(context: Context, uriString: String, maxDim: Int = 320): Bitmap? {
    return try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(Uri.parse(uriString))?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        resolver.openInputStream(Uri.parse(uriString))?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/** 保存视频到系统相册（Vlog 下发相册用） */
fun saveVideoToGallery(context: Context, file: java.io.File): Boolean {
    val name = "PoseAI_${System.currentTimeMillis()}.mp4"
    return try {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/** 分享视频文件（Vlog 预览用） */
fun shareVideo(context: Context, file: java.io.File) {
    val contentUri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享 Vlog"))
}