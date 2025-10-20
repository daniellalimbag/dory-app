package com.thesisapp.communication

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import java.nio.ByteBuffer

class WearReceiver(
    private val context: Context,
    private val onStartRecording: () -> Unit,
    private val onStopRecording: () -> Unit,
    private val onSessionIdReceived: (Int) -> Unit,
    private val isRecordingProvider: () -> Boolean,
    private val hasPendingUploads: () -> Boolean,
    private val onConnectionActive: () -> Unit
) : MessageClient.OnMessageReceivedListener {

    private val TAG = "WearReceiver"
    private val messageClient: MessageClient = Wearable.getMessageClient(context)
    private val errorPath = "/commandError"

    fun register() {
        messageClient.addListener(this)
        Log.d(TAG, "WearReceiver registered")
    }

    fun unregister() {
        messageClient.removeListener(this)
        Log.d(TAG, "WearReceiver unregistered")
    }

    override fun onMessageReceived(event: MessageEvent) {
        onConnectionActive()
        if (event.path == "/sentCommands") {
            val command = event.data.decodeToString()
            Log.d(TAG, "Received command: $command")
            val currentlyRecording = isRecordingProvider()
            if (command == "START" && hasPendingUploads()) {
                sendError(event.sourceNodeId, "PENDING_UPLOAD")
                sendAck(event.sourceNodeId)
                Log.w(TAG, "Ignored START command: pending uploads")
                return
            }
            if (command == "START" && currentlyRecording) {
                sendError(event.sourceNodeId, "ALREADY_RECORDING")
                sendAck(event.sourceNodeId)
                Log.w(TAG, "Ignored START command: already recording")
                return
            } else if (command == "STOP" && !currentlyRecording) {
                sendError(event.sourceNodeId, "NOT_RECORDING")
                sendAck(event.sourceNodeId)
                Log.w(TAG, "Ignored STOP command: not recording")
                return
            }

            sendAck(event.sourceNodeId)

            when (command) {
                "START" -> {
                    Log.d(TAG, "Starting recording")
                    onStartRecording()
                }
                "STOP" -> {
                    Log.d(TAG, "Stopping recording")
                    onStopRecording()
                }
                else -> {
                    Log.w(TAG, "Unknown command: $command")
                    sendError(event.sourceNodeId, "UNKNOWN_COMMAND")
                }
            }
        }
        else if (event.path == "/sentIds"){
            val id = ByteBuffer.wrap(event.data).int
            Log.d(TAG, "Received id: $id")
            onSessionIdReceived(id)

            // Send ACK
            messageClient
                .sendMessage(event.sourceNodeId, "/idAck", "ACK".toByteArray())
                .addOnSuccessListener {
                    Log.d(TAG, "Sent ACK to phone")
                }
                .addOnFailureListener {
                    Log.e(TAG, "Failed to send ACK", it)
                }
        }
        else {
            Log.d(TAG, "Unknown path: ${event.path}")
        }
    }

    private fun sendAck(targetNodeId: String) {
        messageClient
            .sendMessage(targetNodeId, "/commandAck", "ACK".toByteArray())
            .addOnSuccessListener {
                Log.d(TAG, "Sent ACK to phone")
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to send ACK", it)
            }
    }

    private fun sendError(targetNodeId: String, errorCode: String) {
        messageClient
            .sendMessage(targetNodeId, errorPath, errorCode.toByteArray())
            .addOnSuccessListener {
                Log.d(TAG, "Sent error $errorCode to phone")
            }
            .addOnFailureListener {
                Log.e(TAG, "Failed to send error message", it)
            }
    }
}