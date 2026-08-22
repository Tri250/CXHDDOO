package com.poseai.app.util

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/**
 * GIF 导出器——对应 iOS GIFExporter。
 * 将一组连拍帧合成为动画 GIF（灰度近似，稳定输出）。
 */
object GifExporter {

    fun export(frames: List<Bitmap>, delayMs: Int = 180, output: File): Boolean {
        if (frames.isEmpty()) return false
        return try {
            val maxSize = 480
            val base = frames.first()
            val scale = maxSize.toFloat() / maxOf(base.width, base.height)
            val w = (base.width * scale).toInt().coerceAtLeast(1)
            val h = (base.height * scale).toInt().coerceAtLeast(1)

            FileOutputStream(output).use { os ->
                // HDR/透明度统一：画到底图层
                val padded = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(padded)
                canvas.drawColor(android.graphics.Color.BLACK)
                canvas.drawBitmap(frames.first(), null, android.graphics.Rect(0, 0, w, h), null)
                padded.recycle()

                writeHeader(os, w, h)
                writeAppExtension(os, delayMs)
                for (frame in frames) {
                    val scaled = Bitmap.createScaledBitmap(frame, w, h, true)
                    writeFrame(os, scaled, delayMs)
                    if (scaled != frame) scaled.recycle()
                }
                os.write(0x3B) // trailer
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun writeHeader(os: FileOutputStream, w: Int, h: Int) {
        os.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShortLittle(os, w)
        writeShortLittle(os, h)
        os.write(0xF7) // global color table flag, 256 entries, color resolution 7
        os.write(0) // background
        os.write(0) // pixel aspect
        for (i in 0 until 256) { os.write(i); os.write(i); os.write(i) }
    }

    private fun writeAppExtension(os: FileOutputStream, delayMs: Int) {
        // 图形控扩展，让 GIF 能作为动画播放
        val delay = (delayMs / 10).coerceIn(0, 65535)
        os.write(0x21) // extension introducer
        os.write(0xF9) // graphic control
        os.write(4)
        os.write(0x09) // packed: dispose=0, no transparency
        writeShortLittle(os, delay)
        os.write(0) // transparent index
        os.write(0) // block terminator
    }

    private fun writeFrame(os: FileOutputStream, bitmap: Bitmap, delayMs: Int) {
        val w = bitmap.width
        val h = bitmap.height
        os.write(0x2C) // image descriptor
        writeShortLittle(os, 0)
        writeShortLittle(os, 0)
        writeShortLittle(os, w)
        writeShortLittle(os, h)
        os.write(0) // no local color table

        // 每一帧使用「与前帧差分」简化为全量帧，索引为灰度
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val indices = ByteArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val gray = (((c ushr 16) and 0xFF) * 3 + ((c ushr 8) and 0xFF) * 6 + (c and 0xFF)) / 10
            indices[i] = gray.toByte()
        }

        // 使用 LZW 编码（索引为 8 位灰度，minCodeSize=8）
        os.write(8)

        val codes = LzwEncoder.encode(indices, 8)

        // 按 255 字节 block 写出
        var block = ByteArray(255)
        var count = 0
        for (b in codes) {
            block[count++] = b
            if (count == 254) {
                block[254] = 255.toByte()
                os.write(block, 0, 255)
                count = 0
            }
        }
        if (count > 0) {
            block[count - 1] = count.toByte()
            os.write(block, 0, count)
        }
        os.write(0) // block terminator
    }

    private fun writeShortLittle(os: FileOutputStream, v: Int) {
        os.write(v and 0xFF)
        os.write((v ushr 8) and 0xFF)
    }
}

/**
 * 标准 GIF LZW 编码器（开放式可变码长）。
 * 返回带固定 1 字节压缩数据的迭代。
 */
private object LzwEncoder {
    fun encode(data: ByteArray, minCodeSize: Int): List<Byte> = buildList {
        val clearCode = 1 shl minCodeSize
        var codeSize = minCodeSize + 1
        val dictionary = HashMap<List<Int>, Int>()
        fun reset() {
            dictionary.clear()
            dictionary[listOf(clearCode)] = clearCode
        }
        reset()
        var nextCode = clearCode + 2
        // 初始清晰码
        var bitAccum = 0
        var bitCount = 0
        fun emit(code: Int) {
            bitAccum = bitAccum or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                add((bitAccum and 0xFF).toByte())
                bitAccum = bitAccum ushr 8
                bitCount -= 8
            }
        }
        emit(clearCode)
        var phrase = ArrayList<Int>()
        for (b in data) {
            if (phrase.isEmpty()) { phrase.add(b.toInt() and 0xFF); continue }
            var key = phrase + b.toInt()
            // 不在字典则输出短语码，新增新码，重置短语
            if (dictionary.containsKey(key)) {
                phrase.add(b.toInt() and 0xFF)
            } else {
                val code = dictionary[key.dropLast(1)]
                emit(code!!)
                if (nextCode < (1 shl codeSize)) {
                    dictionary[key] = nextCode++
                    if (nextCode == (1 shl codeSize) && codeSize < 12) {
                        // 留一位给下一个 clear 标记，扩容
                    }
                    if (nextCode > (1 shl codeSize) - 1 && codeSize < 12) codeSize++
                } else {
                    emit(clearCode)
                    reset()
                    nextCode = clearCode + 2
                    codeSize = minCodeSize + 1
                }
                phrase = ArrayList()
                phrase.add(b.toInt() and 0xFF)
            }
        }
        if (phrase.isNotEmpty()) {
            val code = dictionary[phrase] ?: clearCode
            emit(code)
        }
        emit(clearCode + 1) // end of info
        // flush 剩余位
        if (bitCount > 0) add((bitAccum and 0xFF).toByte())
    }

    private fun integerLog2(v: Int): Int {
        var n = 1
        var p = 1
        while (p < v) { p = p shl 1; n++ }
        return n
    }
}