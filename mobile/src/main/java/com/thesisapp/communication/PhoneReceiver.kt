package com.thesisapp.communication

import android.content.Context
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.SwimData
import com.thesisapp.presentation.StrokeClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.concurrent.TimeUnit

class PhoneReceiver(
    private val context: Context,
    private val liveSensorFlow: MutableStateFlow<SwimData?>
) : MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {

    private val TAG = "PhoneReceiver"
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO)
    private val classifier = StrokeClassifier(context)

    private val textLabel = mutableStateOf("Waiting...")
    val predictedLabel: State<String> get() = textLabel

    // Detailed Sync Status Callbacks
    interface SyncListener {
        fun onSyncTransferStarted()
        fun onSyncCompleted(count: Int)
        fun onSyncFailed(error: String)
    }

    var syncListener: SyncListener? = null

    fun register() {
        Wearable.getMessageClient(context).addListener(this)
        Wearable.getDataClient(context).addListener(this)
        Log.d(TAG, "Phone Receiver registered for Messages and Data.")
    }

    fun unregister() {
        Wearable.getMessageClient(context).removeListener(this)
        Wearable.getDataClient(context).removeListener(this)
        Log.d(TAG, "Phone Receiver unregistered.")
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/sentSensorData") {
            val jsonString = event.data.decodeToString()

            try {
                val sensorData = json.decodeFromString<SwimData>(jsonString)
                val prediction = classifier.addSensorReading(
                    sensorData.accel_x ?: 0f,
                    sensorData.accel_y ?: 0f,
                    sensorData.accel_z ?: 0f,
                    sensorData.gyro_x ?: 0f,
                    sensorData.gyro_y ?: 0f,
                    sensorData.gyro_z ?: 0f
                )

                prediction?.let {
                    textLabel.value = classifier.getLabel(it)
                }

                liveSensorFlow.value = sensorData

            } catch (e: Exception) {
                Log.e(TAG, "Failed to deserialize SensorData: ${e.message}", e)
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path?.startsWith("/syncData/") == true) {
                val sessionId = event.dataItem.uri.lastPathSegment?.toIntOrNull() ?: continue
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val asset = dataMap.getAsset("sensorDataAsset") ?: continue

                syncListener?.onSyncTransferStarted()
                handleSyncData(asset, sessionId)
            }
        }
    }

    private fun handleSyncData(asset: Asset, sessionId: Int) {
        scope.launch {
            try {
                Log.d(TAG, "Processing sync asset for session $sessionId")
                val inputStream: InputStream? = Tasks.await(
                    Wearable.getDataClient(context).getFdForAsset(asset),
                    30, TimeUnit.SECONDS
                ).inputStream

                if (inputStream == null) {
                    syncListener?.onSyncFailed("Could not open data stream from watch.")
                    return@launch
                }

                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val sensorDataList = json.decodeFromString<List<SwimData>>(jsonString)

                Log.d(TAG, "Received ${sensorDataList.size} synced records for session $sessionId")

                val db = AppDatabase.getInstance(context)
                val swimDataDao = db.swimDataDao()

                db.runInTransaction {
                    sensorDataList.forEach { data ->
                        swimDataDao.insert(data)
                    }
                }

                Log.d(TAG, "Sync successful.")
                syncListener?.onSyncCompleted(sensorDataList.size)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing sync data asset", e)
                syncListener?.onSyncFailed("Error processing data: ${e.message}")
            }
        }
    }
}