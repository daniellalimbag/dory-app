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
        private const val COMMAND_ACK_PATH = "/commandAck"
        private const val COMMAND_ERROR_PATH = "/commandError"
        private const val ACK_SETTLE_DELAY_MS = 200L
        private const val ACK_TIMEOUT_MS = 2000L
    }

    fun sendCommand(
        start: Boolean,
        onSuccess: () -> Unit,
        onCommandError: (CommandErrorType) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val message = if (start) "START" else "STOP"
        val messageClient = Wearable.getMessageClient(context)
        val nodeClient = Wearable.getNodeClient(context)

        var ackReceived = false
        var errorReceived = false
        val mainScope = CoroutineScope(Dispatchers.Main)
        var timeoutJob: Job? = null
        var successJob: Job? = null

        lateinit var ackListener: MessageClient.OnMessageReceivedListener

        fun cleanup() {
            timeoutJob?.cancel()
            successJob?.cancel()
            messageClient.removeListener(ackListener)
        }

        ackListener = object : MessageClient.OnMessageReceivedListener {
            override fun onMessageReceived(event: MessageEvent) {
                when (event.path) {
                    COMMAND_ACK_PATH -> {
                        if (errorReceived) {
                            return
                        }
                        Log.d(TAG, "ACK received")
                        ackReceived = true
                        timeoutJob?.cancel()
                        successJob?.cancel()
                        successJob = mainScope.launch {
                            delay(ACK_SETTLE_DELAY_MS)
                            if (!errorReceived) {
                                cleanup()
                                onSuccess()
                            }
                        }
                    }
                    COMMAND_ERROR_PATH -> {
                        errorReceived = true
                        timeoutJob?.cancel()
                        successJob?.cancel()
                        val payload = runCatching { event.data.decodeToString() }.getOrNull()
                        val errorType = CommandErrorType.fromPayload(payload)
                        Log.w(TAG, "Command error received: $payload")
                        cleanup()
                        mainScope.launch { onCommandError(errorType) }
                    }
                }
            }
        }

        messageClient.addListener(ackListener)

        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    cleanup()
                    onFailure("No connected watch nodes")
                    return@addOnSuccessListener
                }

                // Send the message to all nodes
                for (node in nodes) {
                    messageClient.sendMessage(
                        node.id,
                        COMMAND_PATH,
                        message.encodeToByteArray()
                    )
                        .addOnSuccessListener {
                            Log.d(TAG, "Command '$message' sent to ${node.displayName}")

                            // Start timeout ONLY AFTER sending message
                            timeoutJob = mainScope.launch {
                                delay(ACK_TIMEOUT_MS)
                                if (!ackReceived) {
                                    Log.w(TAG, "ACK timeout")
                                    cleanup()
                                    onFailure("Watch did not acknowledge command")
                                }
                            }
                        }
                        .addOnFailureListener {
                            Log.e(TAG, "Failed to send message", it)
                            cleanup()
                            timeoutJob?.cancel()
                            onFailure("Failed to send command: ${it.message}")
                        }
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to get connected nodes", it)
                cleanup()
                onFailure("Unable to reach connected nodes: ${it.message}")
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

                // Send the message to all nodes
                for (node in nodes) {
                    messageClient.sendMessage(
                        node.id,
                        "/sentIds",
                        ByteBuffer.allocate(4).putInt(id).array()
                    )
                        .addOnSuccessListener {
                            Log.d(TAG, "Session ID $id sent to ${node.displayName}")

                            // Start timeout ONLY AFTER sending message
                            timeoutJob = mainScope.launch {
                                delay(2000L)
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
}
