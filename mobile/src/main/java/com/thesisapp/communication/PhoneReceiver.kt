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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger

class PhoneReceiver(
    private val context: Context,
    private val liveSensorFlow: MutableStateFlow<SwimData?>,
    private val pendingUploadState: MutableStateFlow<Boolean>
) : MessageClient.OnMessageReceivedListener {
    private val TAG = "PhoneReceiver"
    private val json = Json { ignoreUnknownKeys = true }

    private val classifier = StrokeClassifier(context)

    private val textLabel = mutableStateOf("Waiting...")
    val predictedLabel: State<String> get() = textLabel

    private val sessionLock = Any()
    private val pendingSessions = mutableMapOf<Int, SessionAssembly>()
    private val activeWrites = AtomicInteger(0)

    fun register() {
        Wearable.getMessageClient(context).addListener(this)
        Log.d(TAG, "Phone Receiver registered.")
    }

    fun unregister() {
        Wearable.getMessageClient(context).removeListener(this)
        Log.d(TAG, "Phone Receiver unregistered.")
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            "/sentSensorData" -> handleLiveSensorData(event.data)
            "/sessionData" -> handleSessionChunk(event.data)
            "/commandAck", "/commandError" -> Unit // handled explicitly by PhoneSender when awaiting responses
            else -> Log.d(TAG, "Unknown path: ${event.path}")
        }

        val timestamp: Long = System.currentTimeMillis()
        Log.d("WearReceiverService", "PHONE received data at timestamp: $timestamp")
    }

    private fun handleLiveSensorData(raw: ByteArray) {
        val jsonString = raw.decodeToString()

        try {
            val sensorData = json.decodeFromString<SwimData>(jsonString)
            Log.d(TAG, "Received sensor data: $sensorData")

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
    }

    private fun handleSessionChunk(raw: ByteArray) {
        val jsonString = raw.decodeToString()
        val payload = try {
            json.decodeFromString<SessionChunkPayload>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize SessionChunkPayload: ${e.message}", e)
            return
        }

        pendingUploadState.value = true

        var persistenceWork: Pair<Int, List<SwimData>>? = null

        synchronized(sessionLock) {
            val chunkIndex = payload.chunkIndex
            val totalChunks = payload.totalChunks.takeIf { it > 0 } ?: 1
            val assembly = pendingSessions.getOrPut(payload.sessionId) {
                SessionAssembly(totalChunks = totalChunks)
            }

            if (chunkIndex < 0) {
                Log.w(TAG, "Ignoring negative chunk index for session ${payload.sessionId}")
                return
            }

            if (assembly.totalChunks != totalChunks) {
                Log.w(TAG, "Adjusting total chunk count for session ${payload.sessionId} from ${assembly.totalChunks} to $totalChunks")
                assembly.totalChunks = totalChunks
            }

            if (chunkIndex >= assembly.totalChunks) {
                Log.w(TAG, "Chunk index ${chunkIndex} out of range for session ${payload.sessionId}")
                return
            }

            if (assembly.chunks.containsKey(chunkIndex)) {
                Log.w(TAG, "Duplicate chunk ${chunkIndex} for session ${payload.sessionId}")
                return
            }

            val swimRecords = payload.records.map { it.toSwimData(payload.sessionId) }
            assembly.chunks[chunkIndex] = swimRecords
            Log.d(TAG, "Stored chunk ${chunkIndex + 1}/${assembly.totalChunks} for session ${payload.sessionId}")

            if (assembly.isComplete()) {
                persistenceWork = payload.sessionId to assembly.orderedRecords()
                pendingSessions.remove(payload.sessionId)
                activeWrites.incrementAndGet()
            }
        }

        persistenceWork?.let { (sessionId, records) ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = AppDatabase.getInstance(context).swimDataDao()
                    records.forEach { dao.insert(it) }
                    Log.d(TAG, "Persisted ${records.size} records for session $sessionId")
                } finally {
                    val remainingWrites = activeWrites.decrementAndGet().coerceAtLeast(0)
                    synchronized(sessionLock) {
                        if (remainingWrites == 0 && pendingSessions.isEmpty()) {
                            pendingUploadState.value = false
                        }
                    }
                }
            }
        }
    }

    private data class SessionAssembly(
        var totalChunks: Int,
        val chunks: MutableMap<Int, List<SwimData>> = mutableMapOf()
    ) {
        fun isComplete(): Boolean = totalChunks > 0 && chunks.size >= totalChunks

        fun orderedRecords(): List<SwimData> = chunks.toSortedMap().values.flatten()
    }
}