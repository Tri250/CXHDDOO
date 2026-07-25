package com.poseai.app.engine

import android.content.Context
import android.graphics.Bitmap
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

class SceneClassifier(context: Context, modelFilename: String = "scene_model.tflite") {

    private var interpreter: Interpreter? = null
    private val imageProcessor: ImageProcessor
    private val inputSize = 224

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

        try {
            val modelFile = File(context.filesDir, modelFilename)
            if (modelFile.exists()) {
                interpreter = Interpreter(modelFile)
                Log.i("SceneClassifier", "Loaded model from ${modelFile.absolutePath}")
            } else {
                val assetMapped = FileUtil.loadMappedFile(context, modelFilename)
                interpreter = Interpreter(assetMapped)
                Log.i("SceneClassifier", "Loaded model from assets")
            }
        } catch (e: Exception) {
            Log.e("SceneClassifier", "Failed to load model: ${e.message}")
            interpreter = null
        }
    }

    fun classify(bitmap: Bitmap): SceneType {
        val interp = interpreter ?: return SceneType.UNKNOWN

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
            Log.e("SceneClassifier", "Classification failed", e)
            SceneType.UNKNOWN
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
