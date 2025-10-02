package com.thesisapp.presentation

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale



class StrokeClassifier(context: Context) {
    companion object {
        private const val NUM_AXES = 6
        private const val FEATURE_COUNT = NUM_AXES * 4
        private const val WINDOW_SIZE = 29
    }

    private val interpreter: Interpreter = Interpreter(loadModelFile(context, "model.tflite"))
    private val sensorWindow = mutableListOf<FloatArray>()

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    // function must be called for every new reading then model will return predicted class index once buffer is full ; else null
    fun addSensorReading(accelX: Float, accelY: Float, accelZ: Float, gyroX: Float, gyroY: Float, gyroZ: Float): Int? {
        val reading = floatArrayOf(accelX, accelY, accelZ, gyroX, gyroY, gyroZ)
        sensorWindow.add(reading)

        return if (sensorWindow.size >= WINDOW_SIZE) {
            val result = classify(sensorWindow)
            val label = getLabel(result)
            onMLResultReady(label) // ⬅️ Now the function is called
            sensorWindow.clear()
            result
        } else null
    }

    private fun classify(window: List<FloatArray>): Int {
        val features = extractFeatures(window)
        val inputBuffer = ByteBuffer.allocateDirect(4 * FEATURE_COUNT).order(ByteOrder.nativeOrder())
        features.forEach { inputBuffer.putFloat(it) }
        inputBuffer.rewind()

        val output = Array(1) { FloatArray(4) }
        interpreter.run(inputBuffer, output)

        return output[0].indices.maxByOrNull { output[0][it] } ?: -1
    }

    private fun extractFeatures(window: List<FloatArray>): FloatArray {
        val axisData = Array(NUM_AXES) { mutableListOf<Float>() }
        for (reading in window) {
            for (i in 0 until NUM_AXES) {
                axisData[i].add(reading[i])
            }
        }

        val features = FloatArray(FEATURE_COUNT)
        for (i in 0 until NUM_AXES) {
            val mean = axisData[i].average().toFloat()
            val std = sqrt(axisData[i].map { (it - mean) * (it - mean) }.average()).toFloat()
            val min = axisData[i].minOrNull() ?: 0f
            val max = axisData[i].maxOrNull() ?: 0f

            features[i * 4 + 0] = mean
            features[i * 4 + 1] = std
            features[i * 4 + 2] = min
            features[i * 4 + 3] = max
        }

        return features
    }

    fun getLabel(index: Int): String {
        return listOf("Backstroke", "Breaststroke", "Butterfly", "Freestyle").getOrElse(index) { "Unknown" }
    }

    private fun onMLResultReady(result: String) {
        val timestamp: Long = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val formatted = dateFormat.format(Date(timestamp))
        Log.d("MLProcessor", "Detected stroke: $result at $formatted")
    }
}
