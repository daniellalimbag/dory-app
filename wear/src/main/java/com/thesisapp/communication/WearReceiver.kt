package com.thesisapp.communication

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.*
import com.google.android.gms.tasks.Task
import com.thesisapp.data.AppDatabase
import com.thesisapp.presentation.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer

class WearReceiver(
    private val context: Context,
    private val onStartRecording: () -> Unit,
    private val onStopRecording: () -> Unit
) : MessageClient.OnMessageReceivedListener {

    private val TAG = "WearReceiver"
    private val scope = CoroutineScope(Dispatchers.IO)

    fun register() {
        Wearable.getMessageClient(context).addListener(this)
        Log.d(TAG, "WearReceiver registered")
    }

    fun unregister() {
        Wearable.getMessageClient(context).removeListener(this)
        Log.d(TAG, "WearReceiver unregistered")
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            "/sentCommands" -> {
                val command = event.data.decodeToString()
                Log.d(TAG, "Received command: $command")

                // Send ACK
                sendAck(event.sourceNodeId, "/commandAck")

                when (command) {
                    "START" -> {
                        Log.d(TAG, "Starting recording")
                        onStartRecording()
                    }
                    "STOP" -> {
                        Log.d(TAG, "Stopping recording")
                        onStopRecording()
                    }
                    else -> Log.w(TAG, "Unknown command: $command")
                }
            }
            "/sentIds" -> {
                val id = ByteBuffer.wrap(event.data).int
                Log.d(TAG, "Received id: $id")

                sendAck(event.sourceNodeId, "/idAck")
            }
            "/requestSync" -> {
                val sessionId = ByteBuffer.wrap(event.data).int
                Log.d(TAG, "Received sync request for session: $sessionId")

                // Stop recording just in case it's still running
                onStopRecording()

                handleSyncRequest(sessionId, event.sourceNodeId)
            }
            else -> Log.d(TAG, "Unknown path: ${event.path}")
        }
    }

    private fun sendAck(nodeId: String, path: String) {
        Wearable.getMessageClient(context)
            .sendMessage(nodeId, path, "ACK".toByteArray())
            .addOnSuccessListener { Log.d(TAG, "Sent ACK to phone: $path") }
            .addOnFailureListener { Log.e(TAG, "Failed to send ACK: $path", it) }
    }

    private fun handleSyncRequest(sessionId: Int, nodeId: String) {
        scope.launch {
            try {
                val db = AppDatabase.getInstance(context)
                val sensorDataList = db.sensorDataDao().getSensorDataForSession(sessionId)

                Log.d(TAG, "Found ${sensorDataList.size} records to sync for session $sessionId")

                // Convert to JSON and send as Asset via DataClient
                val jsonString = Json.encodeToString(sensorDataList)
                val asset = Asset.createFromBytes(jsonString.toByteArray())

                val putDataRequest = PutDataMapRequest.create("/syncData/$sessionId").apply {
                    dataMap.putAsset("sensorDataAsset", asset)
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()

                Wearable.getDataClient(context).putDataItem(putDataRequest)
                    .addOnSuccessListener { Log.d(TAG, "Sync Data Item put successfully") }
                    .addOnFailureListener { Log.e(TAG, "Failed to put Sync Data Item", it) }

            } catch (e: Exception) {
                Log.e(TAG, "Error during sync handling", e)
            }
        }
    }
}