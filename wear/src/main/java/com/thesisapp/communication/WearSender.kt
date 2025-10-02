package com.thesisapp.communication

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.thesisapp.data.SensorData
import kotlinx.serialization.json.Json

class WearSender(private val context: Context) {

    private val TAG = "WearSender"
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val messagePath = "/sentSensorData"

    fun sendSensorData(sensorData: SensorData) {
        val payload = json.encodeToString(sensorData).encodeToByteArray()

        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No connected nodes.")
                    return@addOnSuccessListener
                }

                for (node in nodes) {
                    messageClient.sendMessage(node.id, messagePath, payload)
                        .addOnSuccessListener {
                            Log.d(TAG, "Message sent to ${node.displayName}")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Send failed to ${node.displayName}: ${e.message}", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get connected nodes: ${e.message}", e)
            }
        val timestamp: Long = System.currentTimeMillis()
        Log.d("WearSender", "WATCH sending data at timestamp: $timestamp")
    }
}