package com.poseai.app.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.poseai.app.model.SceneType
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

class SceneClassifier(context: Context, modelFilename: String = "scene_model.tflite") {

    companion object {
        private const val TAG = "SceneClassifier"
    }

    private var interpreter: Interpreter? = null
    private val imageProcessor: ImageProcessor
    private val inputSize = 224
    private var useFallback = false
    @Volatile
    private var isClosed = false

    private val labels = listOf(
        "COFFEE_SHOP",
        "STREET",
        "BEACH",
        "PARK",
        "HOME",
        "UNKNOWN"
    )

    init {
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(127.5f, 127.5f))
            .build()

        var loaded = false
        try {
            val modelFile = File(context.filesDir, "ai_models/$modelFilename")
            if (modelFile.exists() && modelFile.length() > 1024) {
                interpreter?.close()
                interpreter = Interpreter(modelFile)
                loaded = true
                Log.i(TAG, "Loaded model from ${modelFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load from filesDir: ${e.message}")
        }

        if (!loaded) {
            try {
                val assetList = context.assets.list("")
                if (assetList?.contains(modelFilename) == true) {
                    val assetMapped = FileUtil.loadMappedFile(context, modelFilename)
                    interpreter?.close()
                    interpreter = Interpreter(assetMapped)
                    loaded = true
                    Log.i(TAG, "Loaded model from assets")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load from assets: ${e.message}")
            }
        }

        useFallback = !loaded
        if (useFallback) {
            Log.i(TAG, "Using heuristic fallback classifier")
        }
    }

    fun classify(bitmap: Bitmap): SceneType {
        if (isClosed) return SceneType.UNKNOWN
        return if (useFallback) {
            classifyFallback(bitmap)
        } else {
            classifyWithModel(bitmap)
        }
    }

    private fun classifyWithModel(bitmap: Bitmap): SceneType {
        val interp = interpreter ?: return classifyFallback(bitmap)
        if (isClosed) return SceneType.UNKNOWN

        return try {
            var tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)
            tensorImage = imageProcessor.process(tensorImage)

            val output = Array(1) { FloatArray(labels.size) }
            interp.run(tensorImage.buffer, output)

            val probabilities = output[0]
            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

            if (maxIndex >= 0 && maxIndex < labels.size && probabilities[maxIndex] > 0.3f) {
                SceneType.valueOf(labels[maxIndex])
            } else {
                SceneType.UNKNOWN
            }
        } catch (e: Exception) {
            Log.e(TAG, "Classification with model failed, using fallback", e)
            classifyFallback(bitmap)
        }
    }

    private fun classifyFallback(bitmap: Bitmap): SceneType {
        var small: Bitmap? = null
        return try {
            small = Bitmap.createScaledBitmap(bitmap, 64, 64, true)
            val stats = computeColorStats(small)

            val scores = mutableMapOf<SceneType, Float>()

            scores[SceneType.BEACH] = scoreBeach(stats)
            scores[SceneType.PARK] = scorePark(stats)
            scores[SceneType.HOME] = scoreHome(stats)
            scores[SceneType.COFFEE_SHOP] = scoreCoffeeShop(stats)
            scores[SceneType.STREET] = scoreStreet(stats)

            val best = scores.maxByOrNull { it.value }
            if (best != null && best.value > 0.25f) {
                best.key
            } else {
                SceneType.UNKNOWN
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback classification failed", e)
            SceneType.UNKNOWN
        } finally {
            small?.recycle()
        }
    }

    data class ColorStats(
        val avgR: Float,
        val avgG: Float,
        val avgB: Float,
        val brightness: Float,
        val contrast: Float,
        val saturation: Float,
        val greenRatio: Float,
        val blueRatio: Float,
        val warmRatio: Float,
        val skyBlueRatio: Float
    )

    private fun computeColorStats(bitmap: Bitmap): ColorStats {
        val width = bitmap.width
        val height = bitmap.height
        val total = width * height
        val pixels = IntArray(total)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var sumLum = 0L
        var sumLumSq = 0L
        var greenPixels = 0
        var bluePixels = 0
        var warmPixels = 0
        var skyBluePixels = 0
        var satSum = 0f

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            sumR += r
            sumG += g
            sumB += b

            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toLong()
            sumLum += lum
            sumLumSq += lum * lum

            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val sat = if (max > 0) (max - min).toFloat() / max else 0f
            satSum += sat

            if (g > r && g > b && g > 80) greenPixels++
            if (b > r && b > g && b > 100) bluePixels++
            if (r > b && r > 80 && g > 60) warmPixels++
            if (b > 150 && b > g && b > r && (r + g) < 300) skyBluePixels++
        }

        val avgR = sumR.toFloat() / total
        val avgG = sumG.toFloat() / total
        val avgB = sumB.toFloat() / total
        val brightness = sumLum.toFloat() / total
        val avgLumSq = sumLumSq.toFloat() / total
        val variance = avgLumSq - brightness * brightness
        val contrast = sqrt(variance.coerceAtLeast(0f))
        val saturation = satSum / total

        return ColorStats(
            avgR = avgR,
            avgG = avgG,
            avgB = avgB,
            brightness = brightness,
            contrast = contrast,
            saturation = saturation,
            greenRatio = greenPixels.toFloat() / total,
            blueRatio = bluePixels.toFloat() / total,
            warmRatio = warmPixels.toFloat() / total,
            skyBlueRatio = skyBluePixels.toFloat() / total
        )
    }

    private fun scoreBeach(stats: ColorStats): Float {
        var score = 0f
        if (stats.skyBlueRatio > 0.15f) score += 0.3f
        if (stats.blueRatio > 0.25f) score += 0.2f
        if (stats.brightness > 140) score += 0.2f
        if (stats.saturation > 0.3f) score += 0.15f
        if (stats.avgB > stats.avgR && stats.avgB > 110) score += 0.15f
        return score.coerceIn(0f, 1f)
    }

    private fun scorePark(stats: ColorStats): Float {
        var score = 0f
        if (stats.greenRatio > 0.25f) score += 0.35f
        if (stats.avgG > stats.avgR && stats.avgG > stats.avgB) score += 0.2f
        if (stats.brightness in 80f..160f) score += 0.15f
        if (stats.saturation > 0.25f) score += 0.15f
        if (stats.avgG > 100) score += 0.15f
        return score.coerceIn(0f, 1f)
    }

    private fun scoreHome(stats: ColorStats): Float {
        var score = 0f
        if (stats.warmRatio > 0.4f) score += 0.25f
        if (stats.brightness in 70f..140f) score += 0.2f
        if (stats.saturation < 0.35f) score += 0.2f
        if (stats.contrast < 60) score += 0.15f
        if (abs(stats.avgR - stats.avgG) < 20) score += 0.1f
        if (stats.avgR > stats.avgB) score += 0.1f
        return score.coerceIn(0f, 1f)
    }

    private fun scoreCoffeeShop(stats: ColorStats): Float {
        var score = 0f
        if (stats.warmRatio > 0.5f) score += 0.3f
        if (stats.brightness < 100) score += 0.25f
        if (stats.avgR > stats.avgB + 20) score += 0.15f
        if (stats.saturation in 0.15f..0.4f) score += 0.15f
        if (stats.contrast < 55) score += 0.15f
        return score.coerceIn(0f, 1f)
    }

    private fun scoreStreet(stats: ColorStats): Float {
        var score = 0f
        if (stats.contrast > 50) score += 0.25f
        if (stats.brightness in 90f..170f) score += 0.2f
        val neutralBalance = abs(stats.avgR - stats.avgG) + abs(stats.avgG - stats.avgB)
        if (neutralBalance < 40) score += 0.25f
        if (stats.saturation in 0.2f..0.45f) score += 0.15f
        if (stats.greenRatio < 0.2f) score += 0.15f
        return score.coerceIn(0f, 1f)
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        try {
            interpreter?.close()
        } catch (_: Exception) {}
        interpreter = null
    }
}
