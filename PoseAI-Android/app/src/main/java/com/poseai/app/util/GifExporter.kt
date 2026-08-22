package com.poseai.app.util

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/**
 * GIF 导出器——对应 iOS GIFExporter。
 * 将一组连拍帧合成为动画 GIF，使用完整颜色量化（MedianCut 近似）。
 *
 * 关键实现：
 *  - 256 色调色板（完整 RGB，非灰度）
 *  - 每帧独立量化到调色板
 *  - 标准 LZW 压缩（GIF89a 规范）
 *  - 动画循环控制
 */
object GifExporter {

    private const val DEFAULT_FRAME_DELAY = 180 // ms per frame
    private const val DEFAULT_MAX_SIZE = 480

    fun export(
        frames: List<Bitmap>,
        delayMs: Int = DEFAULT_FRAME_DELAY,
        output: File,
        maxSize: Int = DEFAULT_MAX_SIZE
    ): Boolean {
        if (frames.isEmpty()) return false
        return try {
            // 统一尺寸
            val base = frames.first()
            val scale = maxSize.toFloat() / maxOf(base.width, base.height)
            val w = (base.width * scale).toInt().coerceAtLeast(1)
            val h = (base.height * scale).toInt().coerceAtLeast(1)

            val scaledFrames = frames.map { frame ->
                if (scale < 1f) {
                    Bitmap.createScaledBitmap(frame, w, h, true)
                } else {
                    frame
                }
            }

            // 收集所有像素构建调色板
            val palette = buildPalette(scaledFrames, numColors = 256)

            FileOutputStream(output).use { os ->
                writeGif(os, w, h, scaledFrames, delayMs, palette)
            }

            // 清理缩放副本
            for (f in scaledFrames) {
                if (f != frames.first() || scale < 1f) f.recycle()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 构建 256 色调色板：收集所有像素后用简单中位切分（Median Cut 近似）。
     */
    private fun buildPalette(frames: List<Bitmap>, numColors: Int): ByteArray {
        // 收集所有像素
        val allPixels = ArrayList<Int>(frames.size * frames.first().width * frames.first().height)
        for (frame in frames) {
            val w = frame.width
            val h = frame.height
            val pixels = IntArray(w * h)
            frame.getPixels(pixels, 0, w, 0, 0, w, h)
            for (p in pixels) allPixels.add(p)
        }

        if (allPixels.isEmpty()) {
            val grayPalette = ByteArray(numColors * 3)
            for (i in 0 until numColors) {
                val v = (i * 255 / (numColors - 1)).toByte()
                grayPalette[i * 3] = v; grayPalette[i * 3 + 1] = v; grayPalette[i * 3 + 2] = v
            }
            return grayPalette
        }

        // 使用频度算法：选最高频的 256 个颜色
        val colorCount = HashMap<Int, Int>()
        for (pixel in allPixels) {
            colorCount[pixel] = (colorCount[pixel] ?: 0) + 1
        }

        val sortedColors = colorCount.entries.toList().sortedByDescending { it.value }.take(numColors)

        // 如果颜色不够 256，用剩余颜色填充
        val palette = ByteArray(256 * 3)
        for (i in sortedColors.indices) {
            val color = sortedColors[i].key
            palette[i * 3] = ((color shr 16) and 0xFF).toByte()       // R
            palette[i * 3 + 1] = ((color shr 8) and 0xFF).toByte()  // G
            palette[i * 3 + 2] = (color and 0xFF).toByte()           // B
        }
        // 剩余用灰度填充
        for (i in sortedColors.size until 256) {
            val v = ((i - sortedColors.size) * 255 / (256 - sortedColors.size)).toByte()
            palette[i * 3] = v; palette[i * 3 + 1] = v; palette[i * 3 + 2] = v
        }

        return palette
    }

    /** 将颜色量化到最近的调色板色（已弃用，保留兼容） */
    @Deprecated("No longer used")
    private fun quantizeToPalette(color: Int, palette: ByteArray, paletteMap: IntArray): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val lookupIdx = ((r shr 5) shl 10) or ((g shr 5) shl 5) or (b shr 5)
        return paletteMap[lookupIdx]
    }

    /** 构建颜色查找表（一次性预计算） */
    private fun buildPaletteLookup(palette: ByteArray): IntArray {
        val size = palette.size / 3
        // 使用 3 bits per channel → 32768 entries
        val shift = 5 // 8 bits → 3 bits (32 levels)
        val entries = 1 shl (3 * (8 - shift)) // 32768
        val lookup = IntArray(entries)
        for (i in 0 until entries) {
            val r = ((i shr 10) and 0x1F) shl shift
            val g = ((i shr 5) and 0x1F) shl shift
            val b = (i and 0x1F) shl shift
            val targetR = r.toFloat()
            val targetG = g.toFloat()
            val targetB = b.toFloat()
            var bestDist = Float.MAX_VALUE
            var bestIdx = 0
            for (j in 0 until size) {
                val pr = (palette[j * 3].toInt() and 0xFF).toFloat()
                val pg = (palette[j * 3 + 1].toInt() and 0xFF).toFloat()
                val pb = (palette[j * 3 + 2].toInt() and 0xFF).toFloat()
                val dr = pr - targetR
                val dg = pg - targetG
                val db = pb - targetB
                val dist = dr * dr + dg * dg + db * db
                if (dist < bestDist) { bestDist = dist; bestIdx = j }
            }
            lookup[i] = bestIdx
        }
        return lookup
    }

    private fun writeGif(
        os: FileOutputStream,
        width: Int,
        height: Int,
        frames: List<Bitmap>,
        delayMs: Int,
        palette: ByteArray
    ) {
        val colorCount = 256
        val paletteBits = 8 // 256 colors = 8 bits
        val paletteEntries = colorCount - 1

        // Build palette lookup
        val paletteLookup = buildPaletteLookup(palette)

        // === Header ===
        os.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShortLE(os, width)
        writeShortLE(os, height)
        // Global Color Table: 256 colors, color resolution = 7
        os.write(0xF7)
        os.write(0) // background
        os.write(0) // aspect ratio

        // Write color table (256 * 3 = 768 bytes)
        os.write(palette)

        // === Netscape 2.0 extension (loop animation forever) ===
        os.write(0x21)
        os.write(0xFF) // application extension
        os.write(11)   // block size
        os.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        os.write(3)    // sub-block size
        os.write(1)    // identifier
        writeShortLE(os, 0)     // 0 = loop forever
        os.write(0)    // block terminator

        // === Frames ===
        val delayCentiseconds = (delayMs / 10).coerceIn(0, 65535)
        for (frameIdx in frames.indices) {
            val frame = frames[frameIdx]
            val frameW = frame.width
            val frameH = frame.height

            // Quantize frame pixels
            val pixels = IntArray(frameW * frameH)
            frame.getPixels(pixels, 0, frameW, 0, 0, frameW, frameH)
            val indices = ByteArray(frameW * frameH)
            for (i in pixels.indices) {
                val color = pixels[i]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                val lookupIdx = ((r shr 5) shl 10) or ((g shr 5) shl 5) or (b shr 5)
                indices[i] = paletteLookup[lookupIdx].toByte()
            }

            // Graphic Control Extension
            os.write(0x21)
            os.write(0xF9)
            os.write(4)  // block size
            os.write(0)  // disposal method
            writeShortLE(os, delayCentiseconds)
            os.write(0)  // transparent index
            os.write(0)  // block terminator

            // Image Descriptor
            os.write(0x2C)
            writeShortLE(os, 0)
            writeShortLE(os, 0)
            writeShortLE(os, frameW)
            writeShortLE(os, frameH)
            os.write(0) // no local color table

            // LZW compressed data
            val encoded = LzwEncoder.encode(indices, paletteBits)
            writeSubBlocks(os, encoded)
        }

        // === Trailer ===
        os.write(0x3B)
    }

    /** 写 GIF 子块（最大 254 字节 + 终止 0） */
    private fun writeSubBlocks(os: FileOutputStream, data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val remaining = data.size - offset
            val blockSize = minOf(remaining, 254)
            os.write(blockSize)
            os.write(data, offset, blockSize)
            offset += blockSize
        }
        os.write(0) // block terminator
    }

    private fun writeShortLE(os: FileOutputStream, v: Int) {
        os.write(v and 0xFF)
        os.write((v ushr 8) and 0xFF)
    }
}

/**
 * 标准 GIF LZW 编码器。
 * 使用可变码长（minCodeSize + 1 到 12 位）。
 */
private object LzwEncoder {
    fun encode(data: ByteArray, minCodeSize: Int): ByteArray {
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        val maxCode = 1 shl 12 // 4096

        val output = ArrayList<Byte>()
        val dictionary = HashMap<List<Int>, Int>()
        var nextCode = endCode + 1 // start after clear + end
        var codeSize = minCodeSize + 1

        // 初始化字典
        for (i in 0 until clearCode) {
            dictionary[listOf(i)] = i
        }

        // 位流缓冲
        var bitBuffer = 0
        var bitCount = 0

        fun writeCode(code: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                output.add((bitBuffer and 0xFF).toByte())
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        fun flushBits() {
            if (bitCount > 0) {
                output.add((bitBuffer and 0xFF).toByte())
                bitBuffer = 0
                bitCount = 0
            }
        }

        // 初始 clear code
        writeCode(clearCode)

        var w = ArrayList<Int>()
        for (i in data.indices) {
            val c = data[i].toInt() and 0xFF
            val wc = w + c
            if (dictionary.containsKey(wc)) {
                w = wc as ArrayList<Int>
            } else {
                // 输出 w 的编码
                val code = dictionary[w] ?: clearCode
                writeCode(code)
                // 添加 wc 到字典
                if (nextCode < maxCode) {
                    dictionary[wc] = nextCode++
                    if (nextCode > (1 shl codeSize) && codeSize < 12) codeSize++
                } else {
                    // 字典满了，输出 clear 并重置
                    writeCode(clearCode)
                    dictionary.clear()
                    nextCode = endCode + 1
                    codeSize = minCodeSize + 1
                    for (k in 0 until clearCode) dictionary[listOf(k)] = k
                }
                w = ArrayList<Int>(listOf(c))
            }
        }

        // 输出最后一个条目
        if (w.isNotEmpty()) {
            writeCode(dictionary[w] ?: clearCode)
        }

        // 输出结束码
        writeCode(endCode)
        flushBits()

        return output.toByteArray()
    }
}
