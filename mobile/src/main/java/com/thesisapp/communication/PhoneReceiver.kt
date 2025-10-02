package com.thesisapp.communication

import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.SwimData
import com.thesisapp.presentation.StrokeClassifier
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch


class PhoneReceiver(private val context: Context, private val liveSensorFlow: MutableStateFlow<SwimData?>) : MessageClient.OnMessageReceivedListener {
    private val TAG = "PhoneReceiver"
    private val json = Json { ignoreUnknownKeys = true }

    private val classifier = StrokeClassifier(context)

    private val textLabel = mutableStateOf("Waiting...")
    val predictedLabel: State<String> get() = textLabel

    fun register() {
        Wearable.getMessageClient(context).addListener(this)
        Log.d(TAG,"Phone Receiver registered.")
    }

    fun unregister() {
        Wearable.getMessageClient(context).removeListener(this)
        Log.d(TAG,"Phone Receiver unregistered.")
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/sentSensorData") {
            val jsonString = event.data.decodeToString()

            try {
                val sensorData = json.decodeFromString<SwimData>(jsonString)
                Log.d(TAG, "Received sensor data: $sensorData")

                val prediction = classifier.addSensorReading(
                    sensorData.accel_x ?: 0f,
                    sensorData.accel_y ?: 0f,
                    sensorData.accel_z ?: 0f,
                    sensorData.gyro_x ?: 0f,
                    sensorData.gyro_y ?: 0f,
                    sensorData.gyro_z ?: 0f)

                prediction?.let {
                    textLabel.value = classifier.getLabel(it)
                }

                // 🔥 Fast UI update
                liveSensorFlow.value = sensorData

                // 💾 Background DB insert
                CoroutineScope(Dispatchers.IO).launch {
                    AppDatabase.getInstance(context)
                        .swimDataDao()
                        .insert(sensorData)
                    Log.d(TAG, "Inserted received sensor data into database.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to deserialize SensorData: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "Unknown path: ${event.path}")
        }

        val timestamp: Long = System.currentTimeMillis()
        Log.d("WearReceiverService", "PHONE received data at timestamp: $timestamp")
    }


}