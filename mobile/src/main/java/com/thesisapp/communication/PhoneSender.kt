package com.thesisapp.communication

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

class PhoneSender(private val context: Context) {

    companion object {
        private const val TAG = "PhoneSender"
        private const val COMMAND_PATH = "/sentCommands"
        private const val ID_PATH = "/sentIds"
        private const val SYNC_PATH = "/requestSync"
        private const val TIMEOUT_MS = 5000L // Increased to 5 seconds for reliability
    }

    fun sendCommand(
        start: Boolean,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val message = if (start) "START" else "STOP"
        val messageClient = Wearable.getMessageClient(context)
        val nodeClient = Wearable.getNodeClient(context)

        var ackReceived = false
        val mainScope = CoroutineScope(Dispatchers.Main)
        var timeoutJob: Job? = null

        val ackListener = object : MessageClient.OnMessageReceivedListener {
            override fun onMessageReceived(event: MessageEvent) {
                if (event.path == "/commandAck") {
                    Log.d(TAG, "ACK received")
                    ackReceived = true
                    timeoutJob?.cancel()
                    messageClient.removeListener(this)
                    onSuccess()
                }
            }
        }

        messageClient.addListener(ackListener)

        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    messageClient.removeListener(ackListener)
                    onFailure()
                    return@addOnSuccessListener
                }

                for (node in nodes) {
                    messageClient.sendMessage(
                        node.id,
                        COMMAND_PATH,
                        message.encodeToByteArray()
                    )
                        .addOnSuccessListener {
                            Log.d(TAG, "Command '$message' sent to ${node.displayName}")

                            timeoutJob = mainScope.launch {
                                delay(TIMEOUT_MS)
                                if (!ackReceived) {
                                    Log.w(TAG, "ACK timeout")
                                    messageClient.removeListener(ackListener)
                                    onFailure()
                                }
                            }
                        }
                        .addOnFailureListener {
                            Log.e(TAG, "Failed to send message", it)
                            messageClient.removeListener(ackListener)
                            timeoutJob?.cancel()
                            onFailure()
                        }
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to get connected nodes", it)
                messageClient.removeListener(ackListener)
                onFailure()
            }
    }

    fun sendId(
        id: Int,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val messageClient = Wearable.getMessageClient(context)
        val nodeClient = Wearable.getNodeClient(context)

        var ackReceived = false
        val mainScope = CoroutineScope(Dispatchers.Main)
        var timeoutJob: Job? = null

        val ackListener = object : MessageClient.OnMessageReceivedListener {
            override fun onMessageReceived(event: MessageEvent) {
                if (event.path == "/idAck") {
                    Log.d(TAG, "ID ACK received")
                    ackReceived = true
                    timeoutJob?.cancel()
                    messageClient.removeListener(this)
                    onSuccess()
                }
            }
        }

        messageClient.addListener(ackListener)

        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    messageClient.removeListener(ackListener)
                    onFailure()
                    return@addOnSuccessListener
                }

                for (node in nodes) {
                    messageClient.sendMessage(
                        node.id,
                        "/sentIds",
                        ByteBuffer.allocate(4).putInt(id).array()
                    )
                        .addOnSuccessListener {
                            Log.d(TAG, "Session ID $id sent to ${node.displayName}")

                            timeoutJob = mainScope.launch {
                                delay(TIMEOUT_MS)
                                if (!ackReceived) {
                                    Log.w(TAG, "ID ACK timeout")
                                    messageClient.removeListener(ackListener)
                                    onFailure()
                                }
                            }
                        }
                        .addOnFailureListener {
                            Log.e(TAG, "Failed to send ID", it)
                            messageClient.removeListener(ackListener)
                            timeoutJob?.cancel()
                            onFailure()
                        }
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to get connected nodes", it)
                messageClient.removeListener(ackListener)
                onFailure()
            }
    }

    fun requestSync(
        sessionId: Int,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val messageClient = Wearable.getMessageClient(context)
        val nodeClient = Wearable.getNodeClient(context)

        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    onFailure()
                    return@addOnSuccessListener
                }

                for (node in nodes) {
                    messageClient.sendMessage(
                        node.id,
                        SYNC_PATH,
                        ByteBuffer.allocate(4).putInt(sessionId).array()
                    )
                        .addOnSuccessListener {
                            Log.d(TAG, "Sync request for session $sessionId sent to ${node.displayName}")
                            onSuccess()
                        }
                        .addOnFailureListener {
                            Log.e(TAG, "Failed to send sync request", it)
                            onFailure()
                        }
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to get connected nodes", it)
                onFailure()
            }
    }
}