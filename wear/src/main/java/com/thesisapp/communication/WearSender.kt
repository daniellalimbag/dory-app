package com.thesisapp.communication

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.thesisapp.data.SensorData
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

class WearSender(private val context: Context) {

    private val TAG = "WearSender"
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val messagePath = "/sentSensorData"
    private val sessionDataPath = "/sessionData"
    private val chunkSize = 10

    fun sendSensorData(sensorData: SensorData) {
        val payload = json.encodeToString(sensorData).encodeToByteArray()

        sendToConnectedNodes(messagePath, payload)
        val timestamp: Long = System.currentTimeMillis()
        Log.d("WearSender", "WATCH sending data at timestamp: $timestamp")
    }

    suspend fun sendSessionData(sessionId: Int, data: List<SensorData>): Boolean {
        if (data.isEmpty()) {
            Log.d(TAG, "No stored data to send for session $sessionId")
            return true
        }

        val chunks = data.chunked(chunkSize)
        val totalChunks = chunks.size
        val nodes = getConnectedNodes()
        if (nodes.isEmpty()) {
            Log.w(TAG, "No connected nodes for session upload.")
            return false
        }

        var delivered = false
        for ((index, chunk) in chunks.withIndex()) {
            val payload = SessionChunkPayload(
                sessionId = sessionId,
                chunkIndex = index,
                totalChunks = totalChunks,
                records = chunk
            )
            val bytes = json.encodeToString(payload).encodeToByteArray()

            var chunkDelivered = false
            for (node in nodes) {
                val success = sendMessageAwait(node.id, sessionDataPath, bytes)
                if (success) {
                    chunkDelivered = true
                    delivered = true
                    Log.d(TAG, "Sent chunk ${index + 1}/$totalChunks to ${node.displayName}")
                }
            }

            if (!chunkDelivered) {
                Log.w(TAG, "Chunk ${index + 1} for session $sessionId not delivered to any node")
                return false
            }
        }

        return delivered
    }

    private fun sendToConnectedNodes(path: String, payload: ByteArray) {
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No connected nodes.")
                    return@addOnSuccessListener
                }

                for (node in nodes) {
                    messageClient.sendMessage(node.id, path, payload)
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
    }

    private suspend fun getConnectedNodes(): List<Node> = suspendCancellableCoroutine { cont ->
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                cont.resume(nodes)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get connected nodes for session upload: ${e.message}", e)
                cont.resume(emptyList())
            }
    }

    private suspend fun sendMessageAwait(nodeId: String, path: String, payload: ByteArray): Boolean = suspendCancellableCoroutine { cont ->
        messageClient.sendMessage(nodeId, path, payload)
            .addOnSuccessListener {
                cont.resume(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send message to node $nodeId: ${e.message}", e)
                cont.resume(false)
            }
    }
}